package com.wassup.dinner.chat.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdAndIdGreaterThanOrderByIdAsc(Long roomId, Long afterId);

    long countByRoomId(Long roomId);
}
