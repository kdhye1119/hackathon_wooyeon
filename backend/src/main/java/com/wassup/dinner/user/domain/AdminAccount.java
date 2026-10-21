package com.wassup.dinner.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 운영자 계정. 참가자(User)와 테이블·로그인 경로를 분리한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String loginId;

    @Column(nullable = false)
    private String passwordHash;

    private String name;

    private LocalDateTime createdAt;

    /** 권한: OWNER 전체 / OPERATOR 매칭 정책 변경 제외 / VIEWER 조회만 */
    public enum Role { OWNER, OPERATOR, VIEWER }

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Role role = Role.OWNER;

    public AdminAccount(String loginId, String passwordHash, String name, LocalDateTime now) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.createdAt = now;
    }

    public AdminAccount(String loginId, String passwordHash, String name, Role role, LocalDateTime now) {
        this(loginId, passwordHash, name, now);
        this.role = role;
    }

    public Role effectiveRole() {
        return role == null ? Role.OWNER : role;
    }
}
