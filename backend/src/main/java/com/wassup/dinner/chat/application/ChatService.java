package com.wassup.dinner.chat.application;

import com.wassup.dinner.application.domain.Application;
import com.wassup.dinner.application.domain.ApplicationRepository;
import com.wassup.dinner.chat.domain.*;
import com.wassup.dinner.common.auth.LoginUser;
import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.content.ConversationContentService.TableContent;
import com.wassup.dinner.matching.domain.DiningTable;
import com.wassup.dinner.matching.domain.DiningTableRepository;
import com.wassup.dinner.notification.application.NotificationService;
import com.wassup.dinner.notification.domain.NotificationType;
import com.wassup.dinner.ops.application.DemoSettings;
import com.wassup.dinner.ops.application.ExceptionCaseService;
import com.wassup.dinner.ops.domain.ExceptionType;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.user.domain.Options;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import com.wassup.dinner.venue.domain.Venue;
import com.wassup.dinner.venue.domain.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class ChatService {

    public static final String ROOM_TOPIC = "/topic/rooms/";

    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final ScheduledRoomPostRepository postRepository;
    private final DiningTableRepository tableRepository;
    private final EventSlotRepository slotRepository;
    private final VenueRepository venueRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ExceptionCaseService exceptionCaseService;
    private final DemoSettings demoSettings;
    private final ChatAccess chatAccess;
    private final TableBriefing briefing;
    private final SimpMessagingTemplate messaging;
    private final AppClock clock;

    @Value("${app.notification.max-retries:3}")
    private int maxRetries;

    public record ChatMessageDto(Long id, Long roomId, String type, Long senderUserId, String senderName,
                                 String content, LocalDateTime createdAt) {
        static ChatMessageDto of(ChatMessage m) {
            return new ChatMessageDto(m.getId(), m.getRoomId(), m.getType().name(), m.getSenderUserId(),
                    m.getSenderName(), m.getContent(), m.getCreatedAt());
        }
    }

    /** 확정 후 공개되는 제한된 소개 (실명·연락처·출생연도·직업 비공개) */
    public record MemberIntro(Long userId, String displayName, String ageBand, String mbti,
                              List<String> interests, List<String> traits, boolean me) {
    }

    public record VenueInfo(String name, String address, String mapUrl, String reservationName, String priceGuide) {
    }

    public record RoomSummary(Long roomId, Long tableId, String title, String slotLabel, LocalDateTime startAt,
                              String status, ChatMessageDto lastMessage) {
    }

    public record RoomDetail(Long roomId, Long tableId, String title, String slotLabel, LocalDateTime startAt,
                             VenueInfo venue, String curation, List<MemberIntro> members, TableContent content,
                             boolean contentPublished, String cancelPolicy) {
    }

    // ------------------------------------------------------------------ 생성·자동 공지

    /** 테이블 확정과 동시에 단체방 생성 + 공지 게시 + 리마인드·콘텐츠 예약. 이미 있으면 그대로 반환 (멱등). */
    @Transactional
    public ChatRoom createForTable(DiningTable table, EventSlot slot, Venue venue) {
        Optional<ChatRoom> existing = roomRepository.findByTableId(table.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        ChatRoom room = roomRepository.save(new ChatRoom(table.getId(), slot.getId(),
                slot.label() + " · 테이블 " + table.getTableCode(), clock.now()));
        post(room.getId(), ChatMessage.Type.SYSTEM, "우연한 식탁 단체방이 열렸어요. 참가자 " + table.getMemberCount() + "명이 초대되었어요.");
        post(room.getId(), ChatMessage.Type.NOTICE, briefing.announcement(slot, table, venue));

        LocalDateTime now = clock.now();
        LocalDateTime reminderAt = slot.startAt().minusDays(1);
        schedulePost(room, table, ScheduledRoomPost.Kind.REMINDER, reminderAt.isBefore(now) ? now : reminderAt);
        schedulePost(room, table, ScheduledRoomPost.Kind.CONTENT, slot.startAt());
        return room;
    }

    private void schedulePost(ChatRoom room, DiningTable table, ScheduledRoomPost.Kind kind, LocalDateTime at) {
        ScheduledRoomPost p = new ScheduledRoomPost(room.getId(), table.getId(), kind, at);
        if (!postRepository.existsByDedupKey(p.getDedupKey())) {
            postRepository.save(p);
        }
    }

    /** 식당이 나중에 배정되거나 테이블 구성이 바뀌었을 때 갱신 공지 */
    @Transactional
    public void postNotice(Long tableId, String content) {
        roomRepository.findByTableId(tableId).ifPresent(r -> post(r.getId(), ChatMessage.Type.NOTICE, content));
    }

    @Transactional
    public void postSystem(Long tableId, String content) {
        roomRepository.findByTableId(tableId).ifPresent(r -> post(r.getId(), ChatMessage.Type.SYSTEM, content));
    }

    @Transactional
    public void close(Long tableId, String reason) {
        roomRepository.findByTableId(tableId).ifPresent(r -> {
            post(r.getId(), ChatMessage.Type.SYSTEM, reason);
            r.setStatus(ChatRoom.Status.CLOSED);
            postRepository.findByTableId(tableId).stream()
                    .filter(p -> p.getStatus() == ScheduledRoomPost.Status.PENDING)
                    .forEach(p -> p.setStatus(ScheduledRoomPost.Status.CANCELED));
        });
    }

    private ChatMessage post(Long roomId, ChatMessage.Type type, String content) {
        ChatMessage m = messageRepository.save(new ChatMessage(roomId, null, "우연한 식탁", type, content, clock.now()));
        messaging.convertAndSend(ROOM_TOPIC + roomId, ChatMessageDto.of(m));
        return m;
    }

    /** 예약 게시 실행 (리마인드: 1일 전, 대화 콘텐츠: 행사 시작 시각). 실패 시 재시도, 반복 실패만 운영자 예외. */
    @Transactional
    public int dispatchDuePosts() {
        LocalDateTime now = clock.now();
        int sent = 0;
        for (ScheduledRoomPost p : postRepository.findByStatusAndScheduledAtLessThanEqualOrderByIdAsc(ScheduledRoomPost.Status.PENDING, now)) {
            p.setAttempts(p.getAttempts() + 1);
            if (ThreadLocalRandom.current().nextDouble() < demoSettings.getNotificationFailureRate()) {
                p.setLastError("단체방 메시지 발송 실패 (모의)");
                if (p.getAttempts() >= maxRetries) {
                    p.setStatus(ScheduledRoomPost.Status.FAILED);
                    exceptionCaseService.open(ExceptionType.NOTIFICATION_FAILED, "roompost:" + p.getId(),
                            "단체방 " + p.getKind() + " 메시지가 " + p.getAttempts() + "회 발송 실패했습니다.",
                            ExceptionCaseService.Refs.table(p.getTableId(), null));
                } else {
                    p.setScheduledAt(now.plusSeconds(30L * p.getAttempts()));
                }
                continue;
            }
            DiningTable table = tableRepository.findById(p.getTableId()).orElse(null);
            if (table == null || table.getStatus() == DiningTable.Status.DISSOLVED) {
                p.setStatus(ScheduledRoomPost.Status.CANCELED);
                continue;
            }
            EventSlot slot = slotRepository.findById(table.getSlotId()).orElseThrow();
            // 시각을 크게 건너뛰어 이미 지난 안내는 보내지 않는다 (행사 시작 뒤 '내일 만나요' 리마인드 등)
            LocalDateTime staleAt = p.getKind() == ScheduledRoomPost.Kind.REMINDER ? slot.startAt() : slot.startAt().plusHours(3);
            if (now.isAfter(staleAt)) {
                p.setStatus(ScheduledRoomPost.Status.CANCELED);
                p.setLastError("발송 시점이 지나 건너뜀");
                continue;
            }
            Venue venue = table.getVenueId() == null ? null : venueRepository.findById(table.getVenueId()).orElse(null);
            List<Application> seated = applicationRepository.findByTableId(table.getId()).stream()
                    .filter(Application::isSeated).toList();
            switch (p.getKind()) {
                case REMINDER -> {
                    post(p.getRoomId(), ChatMessage.Type.REMINDER, briefing.reminder(slot, table, venue));
                    for (Application a : seated) {
                        notificationService.notify(a.getUserId(), NotificationType.EVENT_REMINDER,
                                NotificationService.vars("slot", slot.label(), "venue", venue == null ? "식당 배정 중" : venue.getName(),
                                        "address", venue == null ? "-" : venue.getAddress(), "reservationName", table.getReservationName()),
                                "app:" + a.getId(), a.getId(), table.getId());
                    }
                }
                case CONTENT -> post(p.getRoomId(), ChatMessage.Type.CONTENT, briefing.content(slot, table, venue, traitsOf(seated)));
            }
            p.setStatus(ScheduledRoomPost.Status.SENT);
            p.setSentAt(now);
            sent++;
        }
        return sent;
    }

    // ------------------------------------------------------------------ 참가자 API

    @Transactional
    public ChatMessageDto send(Long roomId, LoginUser user, String content) {
        if (!chatAccess.canAccess(roomId, user)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "단체방 참가자만 메시지를 보낼 수 있습니다.");
        }
        if (content == null || content.isBlank() || content.length() > 1000) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "메시지는 1~1000자로 입력해 주세요.");
        }
        ChatRoom room = roomRepository.findById(roomId).orElseThrow(() -> ApiException.notFound("단체방"));
        if (room.getStatus() == ChatRoom.Status.CLOSED) {
            throw new ApiException(ErrorCode.INVALID_STATE, "종료된 단체방입니다.");
        }
        String name = user.isAdmin() ? "운영자" : userRepository.findById(user.userId()).map(u -> displayName(u.getName())).orElse("참가자");
        ChatMessage m = messageRepository.save(new ChatMessage(roomId, user.isAdmin() ? null : user.userId(), name,
                user.isAdmin() ? ChatMessage.Type.SYSTEM : ChatMessage.Type.USER, content.trim(), clock.now()));
        ChatMessageDto dto = ChatMessageDto.of(m);
        messaging.convertAndSend(ROOM_TOPIC + roomId, dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> history(Long roomId, LoginUser user, Long afterId) {
        if (!chatAccess.canAccess(roomId, user)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return messageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId, afterId == null ? 0L : afterId)
                .stream().map(ChatMessageDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<RoomSummary> myRooms(Long userId) {
        List<Long> tableIds = applicationRepository.findByUserIdAndTableIdIsNotNullOrderByIdDesc(userId).stream()
                .filter(Application::isSeated).map(Application::getTableId).toList();
        List<RoomSummary> result = new ArrayList<>();
        for (ChatRoom r : roomRepository.findByTableIdIn(tableIds)) {
            EventSlot slot = slotRepository.findById(r.getSlotId()).orElseThrow();
            List<ChatMessage> msgs = messageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(r.getId(), 0L);
            ChatMessageDto last = msgs.isEmpty() ? null : ChatMessageDto.of(msgs.get(msgs.size() - 1));
            result.add(new RoomSummary(r.getId(), r.getTableId(), r.getTitle(), slot.label(), slot.startAt(), r.getStatus().name(), last));
        }
        result.sort(Comparator.comparing(RoomSummary::startAt).reversed());
        return result;
    }

    @Transactional(readOnly = true)
    public RoomDetail detail(Long roomId, LoginUser user) {
        if (!chatAccess.canAccess(roomId, user)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        ChatRoom room = roomRepository.findById(roomId).orElseThrow(() -> ApiException.notFound("단체방"));
        DiningTable table = tableRepository.findById(room.getTableId()).orElseThrow();
        EventSlot slot = slotRepository.findById(table.getSlotId()).orElseThrow();
        Venue venue = table.getVenueId() == null ? null : venueRepository.findById(table.getVenueId()).orElse(null);
        List<Application> seated = applicationRepository.findByTableId(table.getId()).stream().filter(Application::isSeated).toList();
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(seated.stream().map(Application::getUserId).toList()).forEach(u -> users.put(u.getId(), u));
        int year = clock.today().getYear();
        List<MemberIntro> members = seated.stream().map(a -> users.get(a.getUserId())).map(u -> new MemberIntro(
                u.getId(), displayName(u.getName()), TableBriefing.ageBand(year - u.getProfile().getBirthYear()),
                u.getProfile().getMbti(),
                u.getProfile().getInterests().stream().map(i -> Options.Interest.valueOf(i).getLabel()).toList(),
                u.getProfile().getTraits().stream().map(t -> Options.Trait.valueOf(t).getLabel()).toList(),
                u.getId().equals(user.userId()) && !user.isAdmin())).toList();
        boolean published = !clock.now().isBefore(slot.startAt());
        return new RoomDetail(room.getId(), table.getId(), room.getTitle(), slot.label(), slot.startAt(),
                venue == null ? null : new VenueInfo(venue.getName(), venue.getAddress(), venue.getMapUrl(),
                        table.getReservationName(), venue.getPriceGuide()),
                table.getCuration(), members, briefing.contentData(table, traitsOf(seated)), published,
                briefing.cancelPolicy());
    }

    private List<String> traitsOf(List<Application> seated) {
        List<String> traits = new ArrayList<>();
        userRepository.findAllById(seated.stream().map(Application::getUserId).toList())
                .forEach(u -> traits.addAll(u.getProfile().getTraits()));
        return traits;
    }

    /** 참가자 이름은 가입 시 입력한 본명 그대로 노출 */
    public static String displayName(String name) {
        return name == null || name.isBlank() ? "참가자" : name.trim();
    }
}
