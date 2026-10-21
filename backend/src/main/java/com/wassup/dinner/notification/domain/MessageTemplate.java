package com.wassup.dinner.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 운영자가 수정 가능한 알림 템플릿. code = NotificationType 이름. */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    private String title;

    @Column(length = 1000)
    private String body;

    private LocalDateTime updatedAt;

    public MessageTemplate(String code, String title, String body, LocalDateTime now) {
        this.code = code;
        this.title = title;
        this.body = body;
        this.updatedAt = now;
    }
}
