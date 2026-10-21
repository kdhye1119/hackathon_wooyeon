package com.wassup.dinner.safety.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 안전 신고. 신고 내용은 피신고자에게 공개되지 않는다. */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyReport {

    public enum Status { OPEN, ACTIONED, DISMISSED }

    public enum Action { NONE, WARNING, RESTRICTED, BANNED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reporterId;
    private Long reportedId;
    private Long tableId;

    /** 사유 카테고리 (예: 불쾌한 언행, 과도한 연락처 요구, 이성적 접근, 기타) */
    private String category;

    @Column(length = 2000)
    private String detail;

    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Action action = Action.NONE;

    @Column(length = 1000)
    private String adminNote;
    private String handledBy;

    private LocalDateTime createdAt;
    private LocalDateTime handledAt;

    public SafetyReport(Long reporterId, Long reportedId, Long tableId, String category, String detail,
                        String evidenceUrl, LocalDateTime now) {
        this.reporterId = reporterId;
        this.reportedId = reportedId;
        this.tableId = tableId;
        this.category = category;
        this.detail = detail;
        this.evidenceUrl = evidenceUrl;
        this.createdAt = now;
    }
}
