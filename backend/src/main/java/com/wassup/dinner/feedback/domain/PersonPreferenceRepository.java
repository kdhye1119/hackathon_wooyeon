package com.wassup.dinner.feedback.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PersonPreferenceRepository extends JpaRepository<PersonPreference, Long> {

    List<PersonPreference> findByFromUserIdInOrToUserIdIn(Collection<Long> from, Collection<Long> to);

    List<PersonPreference> findByFromUserIdAndType(Long fromUserId, PersonPreference.Type type);

    List<PersonPreference> findByType(PersonPreference.Type type);
}
