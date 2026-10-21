package com.wassup.dinner.chat.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(columnList = "roomId,id"))
public class ChatMessage {

    public enum Type {
        /** 입장·안내 등 시스템 메시지 */
        SYSTEM,
        /** 시간·장소·큐레이션·취소 정책 공지 */
        NOTICE,
        /** 행사 1일 전 리마인드 */
        REMINDER,
        /** 대화 주제·아이스브레이킹 콘텐츠 */
        CONTENT,
        /** 참가자 메시지 */
        USER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long roomId;
    private Long senderUserId;
    private String senderName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Type type;

    @Column(length = 4000)
    private String content;

    private LocalDateTime createdAt;

    public ChatMessage(Long roomId, Long senderUserId, String senderName, Type type, String content, LocalDateTime now) {
        this.roomId = roomId;
        this.senderUserId = senderUserId;
        this.senderName = senderName;
        this.type = type;
        this.content = content;
        this.createdAt = now;
    }
}
