package com.wassup.dinner.chat.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByTableId(Long tableId);

    List<ChatRoom> findByTableIdIn(Collection<Long> tableIds);
}
