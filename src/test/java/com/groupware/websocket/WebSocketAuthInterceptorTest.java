package com.groupware.websocket;

import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UserRepository;
import com.groupware.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @InjectMocks private WebSocketAuthInterceptor interceptor;
    @Mock private JwtUtil jwtUtil;
    @Mock private MessageChannel channel;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private UserRepository userRepository;

    @Test
    void CONNECT_유효한_토큰_Principal_설정() {
        given(jwtUtil.validate("valid-token")).willReturn(true);
        given(jwtUtil.isBlacklisted("valid-token")).willReturn(false);
        given(jwtUtil.getUserId("valid-token")).willReturn("user@test.com");

        Message<?> result = interceptor.preSend(buildConnect("Bearer valid-token"), channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        Principal principal = accessor.getUser();
        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo("user@test.com");
    }

    @Test
    void CONNECT_유효하지않은_토큰_예외() {
        given(jwtUtil.validate("bad-token")).willReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(buildConnect("Bearer bad-token"), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("유효하지 않은 토큰");
    }

    @Test
    void CONNECT_Authorization_헤더_없으면_예외() {
        assertThatThrownBy(() -> interceptor.preSend(buildConnect(null), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("인증 토큰이 없습니다");
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
