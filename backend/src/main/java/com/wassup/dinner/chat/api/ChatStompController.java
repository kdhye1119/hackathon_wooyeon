package com.wassup.dinner.chat.api;

import com.wassup.dinner.chat.application.ChatService;
import com.wassup.dinner.common.config.StompPrincipal;
import com.wassup.dinner.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/** STOMP: SEND /app/rooms/{roomId}/send {"content":"..."} → 브로드캐스트 /topic/rooms/{roomId} */
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatService chatService;

    public record StompSend(String content) {
    }

    public record StompError(String code, String message) {
    }

    @MessageMapping("/rooms/{roomId}/send")
    public void send(@DestinationVariable Long roomId, @Payload StompSend payload, Principal principal) {
        chatService.send(roomId, ((StompPrincipal) principal).loginUser(), payload.content());
    }

    /** 오류는 보낸 사람에게만 /user/queue/errors 로 전달 */
    @MessageExceptionHandler(ApiException.class)
    @SendToUser("/queue/errors")
    public StompError handle(ApiException e) {
        return new StompError(e.getCode().name(), e.getMessage());
    }
}
