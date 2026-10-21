package com.wassup.dinner.content;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 운영자가 등록한 대화 주제 / 아이스브레이킹 게임.
 * category = 관심사 코드(Options.Interest) · GENERAL(모든 테이블) · ICEBREAKER(게임)
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TopicItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 300)
    private String text;

    private LocalDateTime createdAt;

    public TopicItem(String category, String text, LocalDateTime now) {
        this.category = category;
        this.text = text;
        this.createdAt = now;
    }
}
