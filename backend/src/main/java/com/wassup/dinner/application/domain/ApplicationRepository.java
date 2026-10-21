package com.wassup.dinner.application.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByUserIdOrderByIdDesc(Long userId);

    List<Application> findByAppStatusAndMatchStatus(ApplicationStatus appStatus, MatchStatus matchStatus);

    List<Application> findByMatchStatus(MatchStatus matchStatus);

    List<Application> findByTableId(Long tableId);

    List<Application> findByTableIdIn(Collection<Long> tableIds);

    List<Application> findByTableIdAndMatchStatus(Long tableId, MatchStatus matchStatus);

    List<Application> findByAssignedSlotId(Long slotId);

    List<Application> findByPaymentId(Long paymentId);

    List<Application> findByUserIdInAndTableIdIsNotNull(Collection<Long> userIds);

    List<Application> findByUserIdAndTableIdIsNotNullOrderByIdDesc(Long userId);

    List<Application> findAllByOrderByIdDesc();
}
