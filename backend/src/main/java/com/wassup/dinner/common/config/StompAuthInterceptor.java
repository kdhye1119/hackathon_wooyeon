package com.wassup.dinner.common.config;

import com.wassup.dinner.chat.application.ChatAccess;
import com.wassup.dinner.common.auth.LoginUser;
import com.wassup.dinner.common.auth.TokenProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP 인증·인가.
 * CONNECT: Authorization 헤더(Bearer 토큰) 검증 후 세션 사용자 설정
 * SUBSCRIBE /topic/rooms/{id}: 해당 테이블 확정 참가자(또는 운영자)만 허용
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/rooms/(\\d+)$");

    private final TokenProvider tokenProvider;
    private final ChatAccess chatAccess;

    public StompAuthInterceptor(TokenProvider tokenProvider, @Lazy ChatAccess chatAccess) {
        this.tokenProvider = tokenProvider;
        this.chatAccess = chatAccess;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null) {
                header = accessor.getFirstNativeHeader("token");
            }
            LoginUser user = tokenProvider.parse(header)
                    .orElseThrow(() -> new MessageDeliveryException("UNAUTHORIZED: 유효한 토큰이 필요합니다."));
            accessor.setUser(new StompPrincipal(user));
        } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            if (!(accessor.getUser() instanceof StompPrincipal principal)) {
                throw new MessageDeliveryException("UNAUTHORIZED");
            }
            String destination = accessor.getDestination();
            Matcher m = destination == null ? null : ROOM_TOPIC.matcher(destination);
            if (m != null && m.matches() && !chatAccess.canAccess(Long.valueOf(m.group(1)), principal.loginUser())) {
                throw new MessageDeliveryException("FORBIDDEN: 단체방 참가자만 구독할 수 있습니다.");
            }
        } else if (accessor.getCommand() == StompCommand.SEND && !(accessor.getUser() instanceof StompPrincipal)) {
            throw new MessageDeliveryException("UNAUTHORIZED");
        }
        return message;
    }
}
