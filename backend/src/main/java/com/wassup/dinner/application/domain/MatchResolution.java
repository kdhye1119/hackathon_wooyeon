package com.wassup.dinner.application.domain;

import com.wassup.dinner.common.jpa.LongListConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 매칭 실패 후 해결: 대체 일정 목록과 참가자의 선택(일정 이동·이용권 보관·환불). */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchResolution {

    public enum Choice { TRANSFER, KEEP_TICKET, REFUND }

    public enum Status { PENDING, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long applicationId;

    private Long userId;

    @Convert(converter = LongListConverter.class)
    @Column(length = 500)
    private List<Long> alternativeSlotIds = new ArrayList<>();

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Choice choice;

    private Long chosenSlotId;
    private Long newApplicationId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status = Status.PENDING;

    private LocalDateTime offeredAt;
    private LocalDateTime resolvedAt;

    @Version
    private Long version;

    public MatchResolution(Long applicationId, Long userId, List<Long> alternativeSlotIds, String reason, LocalDateTime now) {
        this.applicationId = applicationId;
        this.userId = userId;
        this.alternativeSlotIds = new ArrayList<>(alternativeSlotIds);
        this.reason = reason;
        this.offeredAt = now;
    }
}
