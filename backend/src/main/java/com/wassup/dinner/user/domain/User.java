package com.wassup.dinner.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 참가자 계정. 운영자 계정은 AdminAccount 로 분리되어 있다. */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String phone;

    private String name;

    /** 비밀번호 해시 (PBKDF2). null 이면 비밀번호 없이 테스트 로그인하는 계정 (시연·가상 데이터) */
    private String passwordHash;

    /** 소셜 로그인 연결 (kakao:123 / naver:abc). 휴대폰 번호는 가입 뒤 인증해 등록 */
    @Column(unique = true, length = 120)
    private String socialKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    /** RESTRICTED 상태의 해제 예정일 (null 이면 무기한) */
    private LocalDate restrictedUntil;

    @Embedded
    private Profile profile = new Profile();

    /** 개인정보 수집·이용 + 오픈 단톡방 동의 */
    private LocalDateTime privacyAgreedAt;
    /** (필독) 주의 사항·환불/노쇼 정책 확인 */
    private LocalDateTime policyAgreedAt;

    private LocalDateTime createdAt;

    public User(String phone, String name, LocalDateTime now) {
        this.phone = phone;
        this.name = name;
        this.createdAt = now;
    }

    public boolean hasConsented() {
        return privacyAgreedAt != null && policyAgreedAt != null;
    }

    public boolean isProfileComplete() {
        return name != null && !name.isBlank() && profile.isComplete();
    }

    public boolean canApply(LocalDate today) {
        if (status == AccountStatus.RESTRICTED && restrictedUntil != null && today.isAfter(restrictedUntil)) {
            return true;
        }
        return status.canApply();
    }

    public int ageAt(int year) {
        return year - profile.getBirthYear() + 1;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }
}
