package com.wassup.dinner.matching.domain;

import com.wassup.dinner.common.jpa.LongListConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 자동 매칭 실행 1회의 기록: 대상 회차, 후보 수, 분리·병합·재배치·확정·실패 결과, 알고리즘 버전. */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchRun {

    public enum Trigger { SCHEDULER, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = LongListConverter.class)
    @Column(length = 500)
    private List<Long> slotIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Trigger triggerType;

    private String algorithmVersion;
    /** 실행 당시 정책 스냅샷 (가중치·최소 점수) */
    @Column(length = 300)
    private String policySnapshot;

    private int candidateCount;
    private int excludedCount;
    private int initialTables;
    private int splitCount;
    private int mergeCount;
    private int reallocatedCount;
    private int dissolvedCount;
    private int confirmedTables;
    private int confirmedParticipants;
    private int failedCount;

    @Lob
    private String log;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public MatchRun(List<Long> slotIds, Trigger trigger, String algorithmVersion, LocalDateTime now) {
        this.slotIds = new ArrayList<>(slotIds);
        this.triggerType = trigger;
        this.algorithmVersion = algorithmVersion;
        this.startedAt = now;
    }
}
