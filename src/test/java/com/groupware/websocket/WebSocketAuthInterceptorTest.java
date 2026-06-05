package com.groupware.websocket;

import com.groupware.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @InjectMocks private WebSocketAuthInterceptor interceptor;
    @Mock private JwtUtil jwtUtil;
    @Mock private MessageChannel channel;

    @Test
    void CONNECT_유효한_토큰_Principal_설정() {
        given(jwtUtil.validate("valid-token")).willReturn(true);
        given(jwtUtil.getUserId("valid-token")).willReturn("user@test.com");

        Message<?> result = interceptor.preSend(buildConnect("Bearer valid-token"), channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        Principal principal = accessor.getUser();
        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo("user@test.com");
    }

    @Test
    void CONNECT_유효하지않은_토큰_Principal_미설정() {
        given(jwtUtil.validate("bad-token")).willReturn(false);

        Message<?> result = interceptor.preSend(buildConnect("Bearer bad-token"), channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void CONNECT_Authorization_헤더_없음_Principal_미설정() {
        Message<?> result = interceptor.preSend(buildConnect(null), channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void CONNECT_아닌_프레임_그대로_통과() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/chat");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNotNull();
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private Message<byte[]> buildConnect(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeader != null) {
            accessor.addNativeHeader("Authorization", authHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
