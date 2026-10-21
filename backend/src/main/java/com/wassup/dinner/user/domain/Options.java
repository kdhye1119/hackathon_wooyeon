package com.wassup.dinner.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 신청서(Google Forms) 문항의 선택지를 코드값으로 정의한다.
 * 프론트는 GET /api/meta/options 로 코드와 라벨을 받아 그대로 렌더링한다.
 */
public final class Options {

    private Options() {
    }

    public interface Labeled {
        String name();

        String getLabel();
    }

    @Getter
    @RequiredArgsConstructor
    public enum Gender implements Labeled {
        MALE("남"), FEMALE("여");
        private final String label;
    }

    /** 나의 요즘 관심사 (복수 선택) */
    @Getter
    @RequiredArgsConstructor
    public enum Interest implements Labeled {
        MOVIE_DRAMA("영화/드라마"),
        MUSIC("음악"),
        READING_WRITING("독서/글쓰기"),
        WORKOUT("운동 (헬스, 러닝, 클라이밍 등)"),
        FOOD_CAFE("맛집/카페 탐방"),
        TRAVEL("여행"),
        PHOTO_VIDEO("사진/영상"),
        EXHIBITION_ART("전시/공연/아트"),
        FASHION_BEAUTY("패션/뷰티"),
        SELF_DEVELOPMENT("자기계발/공부");
        private final String label;
    }

    /** 나는 어떤 사람인가요? (복수 선택) */
    @Getter
    @RequiredArgsConstructor
    public enum Trait implements Labeled {
        CALM("잔잔하고 조용한 편"),
        ENERGETIC("활기차고 에너지 있는 편"),
        DEEP_TALK("깊은 대화를 좋아함"),
        HUMOR("가볍고 유머 있는 대화를 좋아함"),
        LISTENER("경청을 잘 함"),
        LEADER("이야기를 이끄는 편");
        private final String label;
    }

    /** 어떤 사람을 만나고 싶으신가요? (복수 선택) */
    @Getter
    @RequiredArgsConstructor
    public enum DesiredType implements Labeled {
        SIMILAR("나와 비슷한 사람"),
        DIFFERENT("나와 다른 자극을 주는 사람"),
        DEEP_TALK("깊은 대화가 되는 사람"),
        HUMOR("편하고 유머 있는 사람"),
        PASSIONATE("열정 있는 사람"),
        CALM_STABLE("잔잔하고 안정적인 사람");
        private final String label;
    }

    /** 식사에 얼마까지 쓸 수 있으신가요? (복수 선택) */
    @Getter
    @RequiredArgsConstructor
    public enum PriceRange implements Labeled {
        P10_20("10,000~20,000원"),
        P20_30("20,000~30,000원"),
        P30_PLUS("30,000원 이상 (상관 없음)");
        private final String label;
    }

    /** 참석 가능 지역. 서울권 외 희망 지역은 신청서의 기타(otherRegion) 텍스트로 받는다. */
    @Getter
    @RequiredArgsConstructor
    public enum Region implements Labeled {
        HONGDAE_HAPJEONG("홍대/합정"),
        JAMSIL("잠실"),
        GANGNAM("강남/신논현"),
        EULJIRO_JONGNO("을지로/종로");
        private final String label;
    }

    public static final java.util.List<String> MBTI_TYPES = java.util.List.of(
            "ISTJ", "ISFJ", "INFJ", "INTJ", "ISTP", "ISFP", "INFP", "INTP",
            "ESTP", "ESFP", "ENFP", "ENTP", "ESTJ", "ESFJ", "ENFJ", "ENTJ");
}
