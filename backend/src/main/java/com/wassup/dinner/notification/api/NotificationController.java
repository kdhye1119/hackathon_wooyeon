package com.wassup.dinner.notification.api;

import com.wassup.dinner.common.auth.LoginUser;
import com.wassup.dinner.notification.application.NotificationService;
import com.wassup.dinner.notification.application.NotificationService.NotificationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Me - Notifications", description = "앱 내 알림함 (실시간 수신은 STOMP /user/queue/notifications)")
@RestController
@RequestMapping("/api/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록")
    @GetMapping
    public List<NotificationDto> list(LoginUser user) {
        return notificationService.myNotifications(user.userId());
    }

    @Operation(summary = "알림 읽음 처리")
    @PostMapping("/{id}/read")
    public void read(LoginUser user, @PathVariable Long id) {
        notificationService.markRead(user.userId(), id);
    }
}
