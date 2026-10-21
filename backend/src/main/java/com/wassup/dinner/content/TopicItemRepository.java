package com.wassup.dinner.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopicItemRepository extends JpaRepository<TopicItem, Long> {

    List<TopicItem> findAllByOrderByIdDesc();
}
