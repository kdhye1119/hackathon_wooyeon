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

    public static final String CANCEL_POLICY = RoomTemplate.CANCEL_POLICY;

    /** 단체방 상단·정보 화면용 취소 정책 */
    public String cancelPolicy() {
        return CANCEL_POLICY;
    }

    /** 테이블 확정 공지 (ROOM_NOTICE + ROOM_CURATION 템플릿) */
    public String announcement(EventSlot slot, DiningTable table, Venue venue, Collection<String> memberTraits) {
        Map<String, String> v = vars(slot, table, venue, memberTraits, null);
        v.put("큐레이션", "✨ " + render(text(RoomTemplate.ROOM_CURATION, true), v) + "\n" + render(text(RoomTemplate.ROOM_CURATION, false), v));
        return "🎉 " + render(text(RoomTemplate.ROOM_NOTICE, true), v) + "\n\n" + render(text(RoomTemplate.ROOM_NOTICE, false), v);
    }

    /** 행사 1일 전 리마인드 (ROOM_REMINDER 템플릿) */
    public String reminder(EventSlot slot, DiningTable table, Venue venue) {
        Map<String, String> v = vars(slot, table, venue, List.of(), null);
        return "⏰ " + render(text(RoomTemplate.ROOM_REMINDER, true), v) + "\n\n" + render(text(RoomTemplate.ROOM_REMINDER, false), v);
    }

    public String content(EventSlot slot, DiningTable table, Venue venue, Collection<String> memberTraits) {
        TableContent c = contentService.generate(table.getCommonInterests(), memberTraits, table.getId());
        Map<String, String> v = vars(slot, table, venue, memberTraits, c);
        String intro = render(text(RoomTemplate.ROOM_TOPICS, false), v);
        StringBuilder sb = new StringBuilder("💬 " + render(text(RoomTemplate.ROOM_TOPICS, true), v) + "\n");
        if (!intro.isBlank()) {
            sb.append(intro).append("\n\n");
        }
        for (int i = 0; i < c.topics().size(); i++) {
            sb.append(i + 1).append(". ").append(c.topics().get(i)).append('\n');
        }
        sb.append("\n🎮 아이스브레이킹\n");
        c.icebreakers().forEach(g -> sb.append("- ").append(g).append('\n'));
        return sb.toString().trim();
    }

    public TableContent contentData(DiningTable table, Collection<String> memberTraits) {
        return contentService.generate(table.getCommonInterests(), memberTraits, table.getId());
    }

    private String text(RoomTemplate t, boolean title) {
        MessageTemplate m = templateRepository.findByCode(t.name()).orElse(null);
        String s = m == null ? null : (title ? m.getTitle() : m.getBody());
        return s == null ? (title ? t.getDefaultTitle() : t.getDefaultBody()) : s;
    }

    /** 단체방 템플릿 변수. 값이 "" 인 변수가 들어간 줄은 render 에서 빠진다 */
    static Map<String, String> vars(EventSlot slot, DiningTable table, Venue venue, Collection<String> traits, TableContent content) {
        Map<String, String> v = new HashMap<>();
        v.put("일시", slot.label().replace(" " + slot.getRegion().getLabel(), ""));
        v.put("지역", slot.getRegion().getLabel());
        v.put("집합시간", slot.getStartTime().minusMinutes(10).toString());
        v.put("식당명", venue == null ? "식당 배정 중" : venue.getName());
        v.put("주소", venue == null ? "확정되면 바로 안내드릴게요" : venue.getAddress());
        v.put("지도", venue == null || venue.getMapUrl() == null ? "" : venue.getMapUrl());
        v.put("예약명", venue == null || table.getReservationName() == null ? "" : table.getReservationName());
        v.put("식사비", venue == null ? "" : venue.getPriceGuide() == null ? venue.getPriceRange().getLabel() : venue.getPriceGuide());
        int n = table.getMemberCount();
        v.put("인원", String.valueOf(n));
        v.put("남", String.valueOf(table.getMaleCount()));
        v.put("여", String.valueOf(table.getFemaleCount()));
        int year = slot.getDate().getYear();
        if (table.getMaxBirthYear() > 0) {
            String young = ageBand(year - table.getMaxBirthYear());
            String old = ageBand(year - table.getMinBirthYear());
            v.put("연령대", young.equals(old) ? young : young + " ~ " + old);
        } else {
            v.put("연령대", "");
        }
        v.put("공통관심사", table.getCommonInterests().isEmpty() ? "각자 다른 관심사 (새로운 이야기가 많을 거예요)"
                : table.getCommonInterests().stream().limit(3).map(i -> Options.Interest.valueOf(i).getLabel()).collect(Collectors.joining(", ")));
        long e = table.getMbtiSummary() == null ? 0
                : java.util.Arrays.stream(table.getMbtiSummary().split(",")).map(String::trim).filter(m -> m.startsWith("E")).count();
        v.put("MBTI", table.getMbtiSummary() == null || table.getMbtiSummary().isBlank() ? ""
                : "E " + e + ", I " + (n - e) + (e > 0 && e < n ? " — 이끄는 분과 들어주는 분이 고르게 섞였어요" : ""));
        v.put("분위기", traits == null ? "" : traits.stream().distinct().limit(3)
                .map(t -> Options.Trait.valueOf(t).getLabel()).collect(Collectors.joining(", ")));
        // 옛 변수 이름 호환
        v.put("성향구성", v.get("MBTI"));
        if (content != null) {
            v.put("대화주제", content.topics().isEmpty() ? "" : content.topics().get(0));
            v.put("게임", content.icebreakers().isEmpty() ? "" : content.icebreakers().get(0));
        }
        return v;
    }

    private static final Pattern VAR = Pattern.compile("\\{\\{(.+?)}}|\\{(\\w+)}");

    /** {{변수}} 치환. 값이 빈 변수가 있는 줄은 빼고, 연속 빈 줄은 하나로 */
    static String render(String s, Map<String, String> vars) {
        StringBuilder out = new StringBuilder();
        for (String line : s.split("\n", -1)) {
            Matcher m = VAR.matcher(line);
            StringBuilder sb = new StringBuilder();
            boolean drop = false;
            while (m.find()) {
                String key = m.group(1) != null ? m.group(1).trim() : m.group(2);
                String val = vars.get(key);
                if (val != null && val.isEmpty()) {
                    drop = true;
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? m.group() : val));
            }
            if (drop) {
                continue;
            }
            m.appendTail(sb);
            out.append(sb).append('\n');
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").strip();
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
                .map(t -> Options.Trait.valueOf(t).getLabel()).collect(Collectors.joining(", "));
        return "👥 " + ages + " " + memberCount + "명 (남 " + male + ", 여 " + female + ")\n"
                + "💡 공통 관심사: " + interests + "\n"
                + "🧭 MBTI 에너지: E " + e + ", I " + (memberCount - e)
                + (e > 0 && e < memberCount ? " — 이끄는 분과 들어주는 분이 고르게 섞였어요" : "") + "\n"
                + "🌿 테이블 분위기: " + vibe;
    }

    static String ageBand(int age) {
        int decade = (age / 10) * 10;
        int rem = age % 10;
        return decade + "대 " + (rem <= 3 ? "초반" : rem <= 6 ? "중반" : "후반");
    }
}
