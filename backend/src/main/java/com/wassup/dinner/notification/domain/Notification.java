package com.wassup.dinner.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 참가자 알림 (앱 푸시 + 앱 내 알림함). 예약 발송·재시도를 지원하며,
 * dedupKey 유니크로 같은 알림이 두 번 생성되지 않는다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {@Index(columnList = "userId"), @Index(columnList = "status,scheduledAt")})
public class Notification {

    public enum Status { PENDING, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private NotificationType type;

    private String title;

    @Column(length = 1000)
    private String body;

    /** 관련 신청/테이블 (앱에서 딥링크용) */
    private Long applicationId;
    private Long tableId;

    private String channel = "PUSH";

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status = Status.PENDING;

    private LocalDateTime scheduledAt;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private int attempts;
    private String lastError;

    @Column(unique = true, length = 150)
    private String dedupKey;

    private LocalDateTime createdAt;

    public Notification(Long userId, NotificationType type, String title, String body,
                        LocalDateTime scheduledAt, String dedupKey, LocalDateTime now) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.scheduledAt = scheduledAt;
        this.dedupKey = dedupKey;
        this.createdAt = now;
    }
}
