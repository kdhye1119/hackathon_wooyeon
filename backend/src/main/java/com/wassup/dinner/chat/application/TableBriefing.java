package com.wassup.dinner.chat.application;

import com.wassup.dinner.content.ConversationContentService;
import com.wassup.dinner.content.ConversationContentService.TableContent;
import com.wassup.dinner.matching.domain.DiningTable;
import com.wassup.dinner.notification.domain.MessageTemplate;
import com.wassup.dinner.notification.domain.MessageTemplateRepository;
import com.wassup.dinner.notification.domain.NotificationType;
import com.wassup.dinner.notification.domain.RoomTemplate;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.user.domain.Options;
import com.wassup.dinner.venue.domain.Venue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 단체방 공지·리마인드·대화 콘텐츠 문구를 만든다. */
@Component
@RequiredArgsConstructor
public class TableBriefing {

    private final ConversationContentService contentService;
    private final MessageTemplateRepository templateRepository;

    public static final String CANCEL_POLICY = RoomTemplate.ROOM_NOTICE.getDefaultBody();

    /** 운영자가 수정한 단체방 공지 본문 (시간·장소·취소 정책 안내) */
    public String cancelPolicy() {
        return text(RoomTemplate.ROOM_NOTICE.name(), false, RoomTemplate.ROOM_NOTICE.getDefaultBody());
    }

    public String announcement(EventSlot slot, DiningTable table, Venue venue) {
        Map<String, String> v = vars(slot, table, venue, null);
        String curation = render(text(RoomTemplate.ROOM_CURATION.name(), false, RoomTemplate.ROOM_CURATION.getDefaultBody()), v);
        return "🎉 " + render(text(RoomTemplate.ROOM_NOTICE.name(), true, RoomTemplate.ROOM_NOTICE.getDefaultTitle()), v) + "\n\n"
                + place(slot, table, venue) + "\n\n"
                + "✨ " + render(text(RoomTemplate.ROOM_CURATION.name(), true, RoomTemplate.ROOM_CURATION.getDefaultTitle()), v) + "\n"
                + (curation.isBlank() ? "" : curation + "\n") + table.getCuration() + "\n\n"
                + render(cancelPolicy(), v);
    }

    public String reminder(EventSlot slot, DiningTable table, Venue venue) {
        Map<String, String> v = vars(slot, table, venue, null);
        String title = render(text(NotificationType.EVENT_REMINDER.name(), true, NotificationType.EVENT_REMINDER.getDefaultTitle()), v);
        return "⏰ " + title + "\n\n" + place(slot, table, venue)
                + "\n\n도착하시면 앱에서 [체크인]을 눌러주세요.\n\n" + render(cancelPolicy(), v);
    }

    public String content(EventSlot slot, DiningTable table, Venue venue, Collection<String> memberTraits) {
        TableContent c = contentService.generate(table.getCommonInterests(), memberTraits, table.getId());
        Map<String, String> v = vars(slot, table, venue, c);
        String intro = render(text(RoomTemplate.ROOM_TOPICS.name(), false, RoomTemplate.ROOM_TOPICS.getDefaultBody()), v);
        StringBuilder sb = new StringBuilder("💬 " + render(text(RoomTemplate.ROOM_TOPICS.name(), true, RoomTemplate.ROOM_TOPICS.getDefaultTitle()), v) + "\n");
        if (!intro.isBlank()) {
            sb.append(intro).append("\n\n");
        }
        for (int i = 0; i < c.topics().size(); i++) {
            sb.append(i + 1).append(". ").append(c.topics().get(i)).append('\n');
        }
        sb.append("\n🎮 아이스브레이킹\n");
        c.icebreakers().forEach(g -> sb.append("· ").append(g).append('\n'));
        return sb.toString().trim();
    }

    public TableContent contentData(DiningTable table, Collection<String> memberTraits) {
        return contentService.generate(table.getCommonInterests(), memberTraits, table.getId());
    }

    private String text(String code, boolean title, String fallback) {
        MessageTemplate t = templateRepository.findByCode(code).orElse(null);
        String s = t == null ? null : (title ? t.getTitle() : t.getBody());
        return s == null ? fallback : s;
    }

    /** 단체방 템플릿 변수 */
    private static Map<String, String> vars(EventSlot slot, DiningTable table, Venue venue, TableContent content) {
        Map<String, String> v = new HashMap<>();
        v.put("식당명", venue == null ? "식당(배정 중)" : venue.getName());
        v.put("주소", venue == null ? "-" : venue.getAddress());
        v.put("집합시간", slot.getStartTime().minusMinutes(10).toString());
        v.put("예약명", table.getReservationName() == null ? "-" : table.getReservationName());
        v.put("인원", String.valueOf(table.getMemberCount()));
        int year = slot.getDate().getYear();
        if (table.getMaxBirthYear() > 0) {
            String young = ageBand(year - table.getMaxBirthYear());
            String old = ageBand(year - table.getMinBirthYear());
            v.put("연령대", young.equals(old) ? young : young + " ~ " + old);
        } else {
            v.put("연령대", "-");
        }
        v.put("공통관심사", table.getCommonInterests().isEmpty() ? "서로 다른 관심사"
                : table.getCommonInterests().stream().limit(3).map(i -> Options.Interest.valueOf(i).getLabel()).collect(Collectors.joining(", ")));
        v.put("성향구성", table.getMbtiSummary() == null ? "-" : table.getMbtiSummary());
        if (content != null) {
            v.put("대화주제", content.topics().isEmpty() ? "" : content.topics().get(0));
            v.put("게임", content.icebreakers().isEmpty() ? "" : content.icebreakers().get(0));
        }
        return v;
    }

    private static final Pattern VAR = Pattern.compile("\\{\\{(.+?)}}|\\{(\\w+)}");

    static String render(String s, Map<String, String> vars) {
        Matcher m = VAR.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1) != null ? m.group(1).trim() : m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement(vars.getOrDefault(key, m.group())));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String place(EventSlot slot, DiningTable table, Venue venue) {
        String when = "📅 " + slot.label().replace(" " + slot.getRegion().getLabel(), "")
                + " (10분 전 도착 권장)";
        if (venue == null) {
            return when + "\n📍 식당은 배정 중이에요. 확정되면 바로 안내드릴게요.";
        }
        return when
                + "\n📍 " + venue.getName() + " · " + venue.getAddress()
                + "\n🗺 " + venue.getMapUrl()
                + "\n🧾 예약명: " + table.getReservationName()
                + "\n💰 " + (venue.getPriceGuide() == null ? venue.getPriceRange().getLabel() : venue.getPriceGuide());
    }

    /** 확정 테이블의 조합 큐레이션 (상세 개인정보·점수 없이 구성 특징만) */
    public static String curation(int memberCount, int minBirthYear, int maxBirthYear, int male, int female,
                                  List<String> commonInterests, List<String> mbtis, Collection<String> traits, int year) {
        String ages = ageBand(year - maxBirthYear) + (ageBand(year - maxBirthYear).equals(ageBand(year - minBirthYear))
                ? "" : " ~ " + ageBand(year - minBirthYear));
        long e = mbtis.stream().filter(m -> m != null && m.startsWith("E")).count();
        String interests = commonInterests.isEmpty() ? "각자 다른 관심사 (새로운 이야기가 많을 거예요)"
                : commonInterests.stream().limit(3).map(i -> Options.Interest.valueOf(i).getLabel()).collect(Collectors.joining(", "));
        String vibe = traits.stream().distinct().limit(3)
                .map(t -> Options.Trait.valueOf(t).getLabel()).collect(Collectors.joining(" · "));
        return "👥 " + ages + " " + memberCount + "명 (남 " + male + " · 여 " + female + ")\n"
                + "💡 공통 관심사: " + interests + "\n"
                + "🧭 MBTI 에너지: E " + e + " · I " + (memberCount - e)
                + (e > 0 && e < memberCount ? " — 이끄는 분과 들어주는 분이 고르게 섞였어요" : "") + "\n"
                + "🌿 테이블 분위기: " + vibe;
    }

    static String ageBand(int age) {
        int decade = (age / 10) * 10;
        int rem = age % 10;
        return decade + "대 " + (rem <= 3 ? "초반" : rem <= 6 ? "중반" : "후반");
    }
}
