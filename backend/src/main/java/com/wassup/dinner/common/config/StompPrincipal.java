package com.wassup.dinner.common.config;

import com.wassup.dinner.common.auth.LoginUser;

import java.security.Principal;

/**
 * WebSocket 세션 사용자. name 은 참가자 userId 문자열이라 convertAndSendToUser(userId, ...) 로 개인 알림을 보낼 수 있다.
 * 운영자는 참가자 ID 와 겹치지 않도록 "admin-{id}" 를 쓴다.
 */
public record StompPrincipal(LoginUser loginUser) implements Principal {

    @Override
    public String getName() {
        return loginUser.isAdmin() ? "admin-" + loginUser.userId() : String.valueOf(loginUser.userId());
    }
}
