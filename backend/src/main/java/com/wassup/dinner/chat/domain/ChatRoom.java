package com.wassup.dinner.chat.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 테이블 단체 채팅방. tableId 유니크로 같은 테이블에 방이 두 번 생기지 않는다. */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    public enum Status { ACTIVE, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long tableId;

    private Long slotId;
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Status status = Status.ACTIVE;

    private LocalDateTime createdAt;

    public ChatRoom(Long tableId, Long slotId, String title, LocalDateTime now) {
        this.tableId = tableId;
        this.slotId = slotId;
        this.title = title;
        this.createdAt = now;
    }
}
