package com.wassup.dinner.venue.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 운영자가 올린 식당 사진 (DB 보관, 해커톤 규모라 별도 파일 저장소 없이) */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(columnList = "venueId"))
public class VenuePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long venueId;

    @Column(length = 100)
    private String contentType;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "MEDIUMBLOB")
    private byte[] data;

    private LocalDateTime createdAt;

    public VenuePhoto(Long venueId, String contentType, byte[] data, LocalDateTime now) {
        this.venueId = venueId;
        this.contentType = contentType;
        this.data = data;
        this.createdAt = now;
    }

    public static String url(Long id) {
        return "/api/venue-photos/" + id;
    }
}
