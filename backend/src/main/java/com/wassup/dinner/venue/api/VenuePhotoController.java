package com.wassup.dinner.venue.api;

import com.wassup.dinner.venue.application.VenuePhotoService;
import com.wassup.dinner.venue.domain.VenuePhoto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** 식당 사진 이미지 (img 태그로 바로 불러오므로 인증 없이 공개) */
@Tag(name = "Venue Photo", description = "식당 사진 이미지")
@RestController
@RequiredArgsConstructor
public class VenuePhotoController {

    private final VenuePhotoService photoService;

    @Operation(summary = "식당 사진 이미지")
    @GetMapping("/api/venue-photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        VenuePhoto p = photoService.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                .body(p.getData());
    }
}
