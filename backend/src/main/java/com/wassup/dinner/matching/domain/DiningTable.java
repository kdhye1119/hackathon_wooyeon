package com.wassup.dinner.matching.domain;

import com.wassup.dinner.common.jpa.StringSetConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** 확정된 식사 테이블. 참가자는 Application.tableId 로 연결된다. */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(columnList = "slotId"))
public class DiningTable {

    public enum Status {
        /** 자동 확정 */
        CONFIRMED,
        /** 확정 후 인원 이탈로 해제 */
        DISSOLVED,
        /** 행사 종료 */
        COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long slotId;
    private Long matchRunId;
    /** 회차 내 테이블 번호 (A, B, C ...) */
    private String tableCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status = Status.CONFIRMED;

    /** INITIAL(최초 조합) / MERGED(미완성 테이블 병합) / REFILLED(취소 후 충원) */
    private String formation;
    private boolean autoConfirmed = true;
    private int memberCount;
    private int reallocatedMembers;

    // ---- 조건별 평가 결과 (7.2)
    private double totalScore;
    private double ageScore;
    private double genderScore;
    private double mbtiScore;
    private double interestScore;
    private double desireScore;
    private double newnessScore;
    private int minBirthYear;
    private int maxBirthYear;
    private int maleCount;
    private int femaleCount;
    private int metBeforePairs;
    private String mbtiSummary;

    @Convert(converter = StringSetConverter.class)
    @Column(length = 300)
    private Set<String> commonInterests = new LinkedHashSet<>();

    /** 참가자에게 보여줄 테이블 조합 큐레이션 문구 */
    @Column(length = 1000)
    private String curation;

    // ---- 식당
    private Long venueId;
    private String reservationName;

    private LocalDateTime confirmedAt;
    private LocalDateTime dissolvedAt;

    public DiningTable(Long slotId, Long matchRunId, String tableCode, String formation, LocalDateTime now) {
        this.slotId = slotId;
        this.matchRunId = matchRunId;
        this.tableCode = tableCode;
        this.formation = formation;
        this.confirmedAt = now;
    }

    public int ageGap() {
        return maxBirthYear - minBirthYear;
    }
}
