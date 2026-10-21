package com.wassup.dinner.safety.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<UserBlock> findByBlockerIdInOrBlockedIdIn(Collection<Long> blockers, Collection<Long> blocked);

    List<UserBlock> findByBlockerId(Long blockerId);
}
