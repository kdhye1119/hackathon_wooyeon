package com.wassup.dinner.slot.api;

import com.wassup.dinner.slot.application.SlotService;
import com.wassup.dinner.slot.application.SlotService.OpenSlotsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Slots", description = "모집 회차 (참가자)")
@RestController
@RequestMapping("/api/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @Operation(summary = "모집 중인 날짜·지역·회차 목록과 신청 마감·매칭 발표 시각")
    @GetMapping
    public OpenSlotsResponse open() {
        return slotService.openSlots();
    }
}
