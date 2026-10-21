package com.wassup.dinner.chat.api;

import com.wassup.dinner.chat.application.ChatService;
import com.wassup.dinner.chat.application.ChatService.ChatMessageDto;
import com.wassup.dinner.chat.application.ChatService.RoomDetail;
import com.wassup.dinner.chat.application.ChatService.RoomSummary;
import com.wassup.dinner.common.auth.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Chat", description = "테이블 단체방. 실시간은 STOMP: SUBSCRIBE /topic/rooms/{roomId}, SEND /app/rooms/{roomId}/send")
@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    public record SendRequest(@NotBlank @Size(max = 1000) String content) {
    }

    @Operation(summary = "내 단체방 목록")
    @GetMapping
    public List<RoomSummary> myRooms(LoginUser user) {
        return chatService.myRooms(user.userId());
    }

    @Operation(summary = "단체방 상세: 식당·큐레이션·구성원 제한 소개·대화 주제/아이스브레이킹")
    @GetMapping("/{roomId}")
    public RoomDetail detail(LoginUser user, @PathVariable Long roomId) {
        return chatService.detail(roomId, user);
    }

    @Operation(summary = "메시지 이력 (afterId 이후, 재접속 시 누락분 동기화용)")
    @GetMapping("/{roomId}/messages")
    public List<ChatMessageDto> messages(LoginUser user, @PathVariable Long roomId,
                                         @RequestParam(required = false) Long afterId) {
        return chatService.history(roomId, user, afterId);
    }

    @Operation(summary = "메시지 전송 (REST 대체 경로, 기본은 STOMP)")
    @PostMapping("/{roomId}/messages")
    public ChatMessageDto send(LoginUser user, @PathVariable Long roomId, @Valid @RequestBody SendRequest request) {
        return chatService.send(roomId, user, request.content());
    }
}
