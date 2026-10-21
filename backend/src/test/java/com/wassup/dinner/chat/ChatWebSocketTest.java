package com.wassup.dinner.chat;

import com.wassup.dinner.application.domain.Application;
import com.wassup.dinner.application.domain.ApplicationRepository;
import com.wassup.dinner.chat.domain.ChatRoomRepository;
import com.wassup.dinner.common.auth.Role;
import com.wassup.dinner.common.auth.TokenProvider;
import com.wassup.dinner.matching.application.MatchingService;
import com.wassup.dinner.matching.domain.MatchRun;
import com.wassup.dinner.ops.application.DemoService;
import com.wassup.dinner.ops.application.DemoService.ScenarioResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 서버 포트에 STOMP 로 접속해 단체방 실시간 메시지와 구독 권한을 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatWebSocketTest {

    @LocalServerPort int port;
    @Autowired DemoService demoService;
    @Autowired MatchingService matchingService;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired ChatRoomRepository roomRepository;
    @Autowired TokenProvider tokenProvider;

    @Test
    void 확정_참가자는_단체방을_구독하고_실시간으로_메시지를_주고받는다() throws Exception {
        ScenarioResponse s = demoService.scenario(7);
        matchingService.run(List.of(s.slots().get(0).id(), s.slots().get(1).id()), MatchRun.Trigger.ADMIN).orElseThrow();
        Application seated = applicationRepository.findById(s.participants().get(0).applicationId()).orElseThrow();
        Long roomId = roomRepository.findByTableId(seated.getTableId()).orElseThrow().getId();

        StompSession session = connect(tokenProvider.issue(seated.getUserId(), Role.USER));
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/rooms/" + roomId, handler(received));
        Thread.sleep(300); // 구독 등록 대기
        session.send("/app/rooms/" + roomId + "/send", Map.of("content", "안녕하세요!"));

        Map<String, Object> msg = received.poll(5, TimeUnit.SECONDS);
        assertThat(msg).isNotNull();
        assertThat(msg.get("content")).isEqualTo("안녕하세요!");
        assertThat(msg.get("type")).isEqualTo("USER");
        session.disconnect();
    }

    @Test
    void 토큰_없이는_STOMP_연결이_거부된다() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(Exception.class);
    }

    private StompSession connect(String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
    }

    private static StompFrameHandler handler(BlockingQueue<Map<String, Object>> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<String, Object>) payload);
            }
        };
    }
}
