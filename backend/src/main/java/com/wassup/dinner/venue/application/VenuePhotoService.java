package com.wassup.dinner.venue.application;

import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.venue.domain.VenuePhoto;
import com.wassup.dinner.venue.domain.VenuePhotoRepository;
import com.wassup.dinner.venue.domain.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/** 식당 사진 등록·삭제·조회. 참가자 매칭 확정 화면에 노출되고, 없으면 앱 로고로 대신한다. */
@Service
@RequiredArgsConstructor
public class VenuePhotoService {

    public static final int MAX_PHOTOS = 10;
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private final VenuePhotoRepository photoRepository;
    private final VenueRepository venueRepository;
    private final AppClock clock;

    @Transactional
    public List<String> upload(Long venueId, List<MultipartFile> files) {
        venueRepository.findById(venueId).orElseThrow(() -> ApiException.notFound("식당"));
        if (files == null || files.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "사진을 선택해 주세요.");
        }
        if (photoRepository.findIds(venueId).size() + files.size() > MAX_PHOTOS) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "식당 사진은 " + MAX_PHOTOS + "장까지 올릴 수 있어요.");
        }
        for (MultipartFile f : files) {
            String type = f.getContentType();
            if (type == null || !type.startsWith("image/")) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "이미지 파일만 올릴 수 있어요.");
            }
            if (f.getSize() > MAX_BYTES) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "사진은 한 장에 5MB 까지 올릴 수 있어요.");
            }
            try {
                photoRepository.save(new VenuePhoto(venueId, type, f.getBytes(), clock.now()));
            } catch (IOException e) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "사진을 읽지 못했어요.");
            }
        }
        return urls(venueId);
    }

    @Transactional
    public void delete(Long venueId, Long photoId) {
        photoRepository.findById(photoId).filter(p -> p.getVenueId().equals(venueId))
                .ifPresent(photoRepository::delete);
    }

    @Transactional(readOnly = true)
    public VenuePhoto get(Long photoId) {
        return photoRepository.findById(photoId).orElseThrow(() -> ApiException.notFound("사진"));
    }

    @Transactional(readOnly = true)
    public List<String> urls(Long venueId) {
        return venueId == null ? List.of() : photoRepository.findIds(venueId).stream().map(VenuePhoto::url).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<String>> urls(Collection<Long> venueIds) {
        Map<Long, List<String>> out = new HashMap<>();
        if (venueIds.isEmpty()) {
            return out;
        }
        for (Object[] row : photoRepository.findIdsByVenues(venueIds)) {
            out.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add(VenuePhoto.url((Long) row[1]));
        }
        return out;
    }
}
