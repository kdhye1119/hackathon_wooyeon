package com.wassup.dinner.content;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/** 테이블 공통 관심사·대화 성향을 반영한 스몰토크 주제와 아이스브레이킹 게임. */
@Service
@RequiredArgsConstructor
public class ConversationContentService {

    public static final String GENERAL = "GENERAL";
    public static final String ICEBREAKER = "ICEBREAKER";
    /** 테이블 유형(구성원 다수 성향)별 주제: TRAIT_DEEP_TALK 등 */
    public static final String TRAIT_PREFIX = "TRAIT_";

    private final TopicItemRepository topicRepository;

    private static final Map<String, List<String>> TOPICS_BY_INTEREST = Map.of(
            "MOVIE_DRAMA", List.of("최근에 정주행한 드라마나 영화, 한 장면만 꼽는다면?", "인생 영화를 친구에게 한 줄로 영업한다면?"),
            "MUSIC", List.of("요즘 플레이리스트 1번 곡과 그 이유는?", "가장 기억에 남는 공연이나 페스티벌은?"),
            "READING_WRITING", List.of("최근 밑줄 그은 문장 하나를 소개한다면?", "글로 남겨두고 싶은 올해의 순간은?"),
            "WORKOUT", List.of("운동을 시작하게 된 계기와 꾸준히 하는 비결은?", "같이 해보고 싶은 운동이나 도전하고 싶은 대회는?"),
            "FOOD_CAFE", List.of("나만 알고 싶은 단골 맛집이나 카페는?", "오늘 이 식당에서 꼭 먹어봐야 할 메뉴를 골라본다면?"),
            "TRAVEL", List.of("다시 가고 싶은 여행지와 그곳에서의 한 장면은?", "여행 스타일: 계획파 vs 즉흥파?"),
            "PHOTO_VIDEO", List.of("휴대폰 사진첩에서 가장 아끼는 사진 한 장 이야기", "요즘 찍고 싶은 장면이나 장소는?"),
            "EXHIBITION_ART", List.of("최근 본 전시/공연 중 마음에 남은 작품은?", "직접 배워보고 싶은 예술 분야가 있다면?"),
            "FASHION_BEAUTY", List.of("요즘 꽂힌 스타일이나 아이템은?", "나를 가장 잘 표현하는 컬러는?"),
            "SELF_DEVELOPMENT", List.of("요즘 배우고 있는 것, 혹은 배우고 싶은 것은?", "올해 안에 꼭 이루고 싶은 작은 목표 하나는?"));

    private static final List<String> DEEP_TOPICS = List.of(
            "요즘 나를 가장 설레게 하는 일은 무엇인가요?",
            "5년 전의 나에게 해주고 싶은 말이 있다면?",
            "나를 나답게 만드는 습관이나 루틴은?");

    private static final List<String> LIGHT_TOPICS = List.of(
            "오늘 하루 중 가장 웃겼던 순간은?",
            "주말에 아무 계획이 없다면 가장 먼저 하는 일은?",
            "최근 산 물건 중 가장 만족스러운 것은?");

    private static final List<String> ICEBREAKERS = List.of(
            "🎲 두 개의 진실, 하나의 거짓: 나에 대한 문장 3개 중 거짓을 맞혀보세요.",
            "🔤 이름 대신 별명으로: 오늘만 쓰고 싶은 별명과 그 이유를 소개해요.",
            "🍽 밸런스 게임: 평생 한식만 vs 평생 양식만 / 아침형 인간 vs 저녁형 인간",
            "🧭 공통점 찾기: 5분 안에 테이블 모두의 공통점 3가지를 찾아보세요.");

    public record TableContent(List<String> topics, List<String> icebreakers) {
    }

    /**
     * @param commonInterests 테이블 공통 관심사 (많이 겹치는 순)
     * @param traits          테이블 구성원 성향 전체
     */
    public TableContent generate(Collection<String> commonInterests, Collection<String> traits, long seed) {
        Random random = new Random(seed);
        // 운영자가 A08 에서 등록한 주제·게임 (최근 등록 순)
        Map<String, List<String>> custom = new HashMap<>();
        topicRepository.findAllByOrderByIdDesc().forEach(t -> custom.computeIfAbsent(t.getCategory(), k -> new ArrayList<>()).add(t.getText()));
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        for (String interest : commonInterests) {
            // 공통 관심사에 운영자 등록 주제가 있으면 그것부터
            List<String> mine = custom.getOrDefault(interest, List.of());
            List<String> list = mine.isEmpty() ? TOPICS_BY_INTEREST.get(interest) : mine;
            if (list != null) {
                topics.add(list.get(random.nextInt(list.size())));
            }
            if (topics.size() >= 2) {
                break;
            }
        }
        // 테이블 유형별 노출 규칙: 구성원 성향 중 가장 많은 성향(동률이면 둘)의 운영자 등록 주제
        Map<String, Long> traitCount = new HashMap<>();
        traits.forEach(t -> traitCount.merge(t, 1L, Long::sum));
        long top = traitCount.values().stream().mapToLong(Long::longValue).max().orElse(0);
        traitCount.entrySet().stream().filter(e -> top > 0 && e.getValue() == top).map(Map.Entry::getKey).sorted().limit(2)
                .forEach(t -> {
                    List<String> typed = custom.getOrDefault(TRAIT_PREFIX + t, List.of());
                    if (!typed.isEmpty()) {
                        topics.add(typed.get(random.nextInt(typed.size())));
                    }
                });
        // 모든 테이블 공통 주제 (운영자 등록) 1개
        List<String> general = custom.getOrDefault(GENERAL, List.of());
        if (!general.isEmpty()) {
            topics.add(general.get(random.nextInt(general.size())));
        }
        long deep = traits.stream().filter(t -> t.equals("DEEP_TALK") || t.equals("CALM") || t.equals("LISTENER")).count();
        long light = traits.stream().filter(t -> t.equals("HUMOR") || t.equals("ENERGETIC") || t.equals("LEADER")).count();
        List<String> primary = deep >= light ? DEEP_TOPICS : LIGHT_TOPICS;
        List<String> secondary = deep >= light ? LIGHT_TOPICS : DEEP_TOPICS;
        List<String> shuffled = new ArrayList<>(primary);
        Collections.shuffle(shuffled, random);
        for (String t : shuffled) {
            if (topics.size() >= 4) {
                break;
            }
            topics.add(t);
        }
        topics.add(secondary.get(random.nextInt(secondary.size())));

        List<String> games = new ArrayList<>(ICEBREAKERS);
        Collections.shuffle(games, random);
        // 운영자 등록 게임이 있으면 하나는 꼭 포함
        List<String> customGames = custom.getOrDefault(ICEBREAKER, List.of());
        if (!customGames.isEmpty()) {
            games.add(0, "🎮 " + customGames.get(random.nextInt(customGames.size())));
        }
        return new TableContent(List.copyOf(topics), games.subList(0, 2));
    }

    /** 카테고리별 기본 제공 주제 수 (A08 대화 주제 라이브러리 표시용) */
    public Map<String, Integer> builtInCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        TOPICS_BY_INTEREST.forEach((k, v) -> out.put(k, v.size()));
        out.put(GENERAL, DEEP_TOPICS.size() + LIGHT_TOPICS.size());
        out.put(ICEBREAKER, ICEBREAKERS.size());
        for (var t : com.wassup.dinner.user.domain.Options.Trait.values()) {
            out.put(TRAIT_PREFIX + t.name(), 0);
        }
        return out;
    }
}
