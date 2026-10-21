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

    public AdminAccount(String loginId, String passwordHash, String name, LocalDateTime now) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.createdAt = now;
    }
}
