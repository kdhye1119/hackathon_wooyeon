package com.wassup.dinner.venue.domain;

import com.wassup.dinner.user.domain.Options.PriceRange;
import com.wassup.dinner.user.domain.Options.Region;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 파트너 식당. tablesPerSlot = 한 회차(같은 날짜·시간)에 받을 수 있는 테이블 수. */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Region region;

    private String address;
    private String mapUrl;
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PriceRange priceRange;

    /** 1인 예상 식사 가격 안내 문구 */
    private String priceGuide;

    private int seatsPerTable = 6;
    private int tablesPerSlot = 2;
    private boolean active = true;

    private LocalDateTime createdAt;

    public Venue(String name, Region region, String address, String mapUrl, PriceRange priceRange,
                 String priceGuide, int tablesPerSlot, LocalDateTime now) {
        this.name = name;
        this.region = region;
        this.address = address;
        this.mapUrl = mapUrl;
        this.priceRange = priceRange;
        this.priceGuide = priceGuide;
        this.tablesPerSlot = tablesPerSlot;
        this.createdAt = now;
    }
}
