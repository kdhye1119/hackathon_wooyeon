package com.wassup.dinner.user.domain;

import com.wassup.dinner.common.jpa.StringSetConverter;
import com.wassup.dinner.user.domain.Options.Gender;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/** 매칭 프로필. 태그 값은 Options 의 enum 이름으로 저장한다. */
@Embeddable
@Getter
@Setter
public class Profile {

    private Integer birthYear;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    private String job;

    private String instagramId;

    @Column(length = 4)
    private String mbti;

    @Convert(converter = StringSetConverter.class)
    @Column(length = 500)
    private Set<String> interests = new LinkedHashSet<>();

    @Convert(converter = StringSetConverter.class)
    @Column(length = 300)
    private Set<String> traits = new LinkedHashSet<>();

    @Convert(converter = StringSetConverter.class)
    @Column(length = 300)
    private Set<String> desiredTypes = new LinkedHashSet<>();

    @Convert(converter = StringSetConverter.class)
    @Column(length = 100)
    private Set<String> priceRanges = new LinkedHashSet<>();

    /** 못 먹는 음식·알레르기 (자유 입력) */
    @Column(length = 500)
    private String foodRestrictions;

    public boolean isComplete() {
        return birthYear != null && gender != null && job != null && !job.isBlank()
                && !interests.isEmpty() && !traits.isEmpty();
    }
}
