package com.wassup.dinner.common.auth;

/** 인증된 요청의 사용자. 컨트롤러 파라미터로 선언하면 자동 주입된다. */
public record LoginUser(Long userId, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public String actor() {
        return (isAdmin() ? "ADMIN:" : "USER:") + userId;
    }
}
