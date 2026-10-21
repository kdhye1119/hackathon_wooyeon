package com.wassup.dinner.matching;

import com.wassup.dinner.matching.engine.MbtiCompatibility;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MbtiCompatibilityTest {

    @Test
    void 궁합표_값과_대칭() {
        assertThat(MbtiCompatibility.score("ENFP", "ESFP")).isEqualTo(93);
        assertThat(MbtiCompatibility.score("ESTJ", "INFP")).isEqualTo(3);
        assertThat(MbtiCompatibility.score("enfj", "infj")).isEqualTo(74);
        for (String a : MbtiCompatibility.TYPES) {
            for (String b : MbtiCompatibility.TYPES) {
                assertThat(MbtiCompatibility.score(a, b)).isEqualTo(MbtiCompatibility.score(b, a));
            }
        }
    }

    @Test
    void 모르는_MBTI는_중간값_등급은_3단계() {
        assertThat(MbtiCompatibility.score(null, "INTJ")).isEqualTo(MbtiCompatibility.UNKNOWN);
        assertThat(MbtiCompatibility.score("XXXX", "INTJ")).isEqualTo(MbtiCompatibility.UNKNOWN);
        assertThat(MbtiCompatibility.grade(70)).isEqualTo("잘 맞는 궁합");
        assertThat(MbtiCompatibility.grade(31)).isEqualTo("보통 궁합");
        assertThat(MbtiCompatibility.grade(30)).isEqualTo("안 맞는 궁합");
    }
}
