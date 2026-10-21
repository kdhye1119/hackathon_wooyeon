package com.wassup.dinner.matching.engine;

import java.util.List;

/**
 * MBTI 궁합 점수 (mbti.md: 외국 MBTI 찐궁합표).
 * 공식으로 떨어지지 않는 표라 16x16 조회 테이블로 구현한다. 0~100, score(A,B) == score(B,A).
 */
public final class MbtiCompatibility {

    public static final List<String> TYPES = List.of("ENFJ", "ENFP", "ENTJ", "ENTP", "ESFJ", "ESFP", "ESTJ", "ESTP", "INFJ", "INFP", "INTJ", "INTP", "ISFJ", "ISFP", "ISTJ", "ISTP");

    private static final int[][] SCORES = {
            {86, 91, 42, 73, 64, 80, 22, 41, 74, 73, 16, 35, 30, 40, 18,  9}, // ENFJ,
            {91, 97, 37, 85, 42, 93, 27, 76, 51, 73, 13, 36, 11, 49,  4, 14}, // ENFP,
            {42, 37, 91, 81, 53, 51, 87, 74, 25, 13, 46, 47, 29,  6, 66, 41}, // ENTJ,
            {73, 85, 81, 94, 32, 87, 70, 92, 11, 35, 22, 51,  5, 14, 11, 35}, // ENTP,
            {64, 42, 53, 32, 94, 40, 77, 37, 74, 17, 32,  5, 79, 57, 71, 19}, // ESFJ,
            {80, 93, 51, 87, 40, 70, 39, 75, 43, 58, 22, 39, 12, 58,  8, 26}, // ESFP,
            {22, 27, 87, 70, 77, 39, 96, 78, 14,  3, 33, 22, 48, 22, 79, 55}, // ESTJ,
            {41, 76, 74, 92, 37, 75, 78, 95,  5, 24, 17, 39, 12, 43, 20, 62}, // ESTP,
            {74, 51, 25, 11, 74, 43, 14,  5, 95, 85, 65, 50, 85, 58, 53, 23}, // INFJ,
            {73, 73, 13, 35, 17, 58,  3, 24, 85, 97, 70, 84, 46, 78, 21, 49}, // INFP,
            {16, 13, 46, 22, 32, 22, 33, 17, 65, 70, 86, 89, 79, 45, 85, 78}, // INTJ,
            {35, 36, 47, 51,  5, 39, 22, 39, 50, 84, 89, 96, 38, 43, 51, 81}, // INTP,
            {30, 11, 29,  5, 79, 12, 48, 12, 85, 46, 79, 38, 95, 76, 93, 62}, // ISFJ,
            {40, 49,  6, 14, 57, 58, 22, 43, 58, 78, 45, 43, 76, 97, 47, 76}, // ISFP,
            {18,  4, 66, 11, 71,  8, 79, 20, 53, 21, 85, 51, 93, 47, 96, 78}, // ISTJ,
            { 9, 14, 41, 35, 19, 26, 55, 62, 23, 49, 78, 81, 62, 76, 78, 96}, // ISTP
    };

    /** MBTI 를 모르는 참가자가 낀 쌍은 중간값으로 본다 */
    public static final int UNKNOWN = 50;

    private MbtiCompatibility() {
    }

    /** 두 유형의 궁합 점수 0~100 */
    public static int score(String a, String b) {
        int i = index(a);
        int j = index(b);
        return i < 0 || j < 0 ? UNKNOWN : SCORES[i][j];
    }

    /** 원본 표 색 기준 3단계: 70~ 잘 맞는 궁합 / 31~69 보통 / ~30 안 맞는 궁합 */
    public static String grade(int score) {
        if (score >= 70) return "잘 맞는 궁합";
        if (score >= 31) return "보통 궁합";
        return "안 맞는 궁합";
    }

    private static int index(String t) {
        return t == null ? -1 : TYPES.indexOf(t.trim().toUpperCase());
    }
}
