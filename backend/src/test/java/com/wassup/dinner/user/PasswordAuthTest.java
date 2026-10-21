package com.wassup.dinner.user;

import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.user.application.AuthService;
import com.wassup.dinner.user.application.OtpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 참가자 비밀번호: 인증번호 → 가입 → 로그인 / 재설정 */
@SpringBootTest
@ActiveProfiles("test")
class PasswordAuthTest {

    @Autowired AuthService authService;

    @Test
    void 가입하면_비밀번호가_맞아야_로그인() {
        String phone = "01055550001";
        String code = authService.requestOtp(phone, OtpService.Purpose.SIGNUP);
        assertThat(authService.signup(phone, code, "abcd1234", "가입자").token()).isNotBlank();

        assertThat(authService.participantLogin(phone, null, "abcd1234").token()).isNotBlank();
        assertThatThrownBy(() -> authService.participantLogin(phone, null, "wrong1234")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> authService.participantLogin(phone, null, null)).isInstanceOf(ApiException.class);
        // 이미 가입한 번호는 다시 가입 인증번호를 받을 수 없다
        assertThatThrownBy(() -> authService.requestOtp(phone, OtpService.Purpose.SIGNUP)).isInstanceOf(ApiException.class);
    }

    @Test
    void 인증번호가_틀리거나_재사용하면_거절() {
        String phone = "01055550002";
        String code = authService.requestOtp(phone, OtpService.Purpose.SIGNUP);
        String wrong = code.equals("000000") ? "111111" : "000000";
        assertThatThrownBy(() -> authService.signup(phone, wrong, "abcd1234", null)).isInstanceOf(ApiException.class);
        authService.signup(phone, code, "abcd1234", null);
        assertThatThrownBy(() -> authService.resetPassword(phone, code, "efgh5678")).isInstanceOf(ApiException.class);
    }

    @Test
    void 비밀번호_규칙() {
        String phone = "01055550003";
        String code = authService.requestOtp(phone, OtpService.Purpose.SIGNUP);
        assertThatThrownBy(() -> authService.signup(phone, code, "short1", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> authService.signup(phone, code, "onlyletters", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> authService.signup(phone, code, "12345678", null)).isInstanceOf(ApiException.class);
    }

    @Test
    void 재설정하면_새_비밀번호로만_로그인() {
        String phone = "01055550004";
        authService.signup(phone, authService.requestOtp(phone, OtpService.Purpose.SIGNUP), "abcd1234", null);
        authService.resetPassword(phone, authService.requestOtp(phone, OtpService.Purpose.RESET), "efgh5678");
        assertThat(authService.participantLogin(phone, null, "efgh5678").token()).isNotBlank();
        assertThatThrownBy(() -> authService.participantLogin(phone, null, "abcd1234")).isInstanceOf(ApiException.class);
    }

    @Test
    void 비밀번호_없는_테스트_계정은_번호만으로_로그인() {
        assertThat(authService.participantLogin("01055550005", "테스트", null).token()).isNotBlank();
        assertThat(authService.participantLogin("01055550005", null, null).token()).isNotBlank();
        // 없는 번호로 비밀번호 로그인을 시도하면 가입 안내
        assertThatThrownBy(() -> authService.participantLogin("01055550006", null, "abcd1234")).isInstanceOf(ApiException.class);
    }
}
