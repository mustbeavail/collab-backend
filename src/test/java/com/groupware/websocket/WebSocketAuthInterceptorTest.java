package com.groupware.websocket;

import com.groupware.service.RoomMembershipChecker;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @InjectMocks private WebSocketAuthInterceptor interceptor;
    @Mock private JwtUtil jwtUtil;
    @Mock private MessageChannel channel;
    @Mock private RoomMembershipChecker roomMembershipChecker;

    // ─── CONNECT ─────────────────────────────────────────────────────────

    @Test
    void CONNECT_유효한_토큰_Principal_설정() {
        given(jwtUtil.validate("valid-token")).willReturn(true);
        given(jwtUtil.isBlacklisted("valid-token")).willReturn(false);
        given(jwtUtil.isCurrentSession("valid-token")).willReturn(true);
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
    void CONNECT_다른기기_로그인_세션_불일치_예외() {
        // 새 로그인으로 sid가 갱신되어 기존 토큰은 현재 세션이 아님 → WS 연결 거부
        given(jwtUtil.validate("stale-token")).willReturn(true);
        given(jwtUtil.isBlacklisted("stale-token")).willReturn(false);
        given(jwtUtil.isCurrentSession("stale-token")).willReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(buildConnect("Bearer stale-token"), channel))
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

    // ─── SUBSCRIBE 멤버십 인가 ────────────────────────────────────────────

    @Test
    void SUBSCRIBE_voice_멤버_통과() {
        given(roomMembershipChecker.isMember(5L, "user@test.com")).willReturn(true);

        Message<?> result = interceptor.preSend(
                buildSubscribe("/topic/voice/5", principal("user@test.com")), channel);

        assertThat(result).isNotNull();
        verify(roomMembershipChecker).isMember(5L, "user@test.com");
    }

    @Test
    void SUBSCRIBE_voice_비멤버_예외() {
        given(roomMembershipChecker.isMember(5L, "user@test.com")).willReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(
                buildSubscribe("/topic/voice/5", principal("user@test.com")), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("구독 권한");
    }

    @Test
    void SUBSCRIBE_chart_비멤버_예외() {
        given(roomMembershipChecker.isMember(5L, "user@test.com")).willReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(
                buildSubscribe("/topic/chart/5", principal("user@test.com")), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("구독 권한");
    }

    @Test
    void SUBSCRIBE_room_비멤버_예외_회귀방지() {
        given(roomMembershipChecker.isMember(5L, "user@test.com")).willReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(
                buildSubscribe("/topic/room/5", principal("user@test.com")), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("구독 권한");
    }

    @Test
    void SUBSCRIBE_Principal_없으면_예외() {
        assertThatThrownBy(() -> interceptor.preSend(
                buildSubscribe("/topic/voice/5", null), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("인증되지 않은 구독");
        verifyNoInteractions(roomMembershipChecker);
    }

    @Test
    void SUBSCRIBE_잘못된_경로_예외() {
        assertThatThrownBy(() -> interceptor.preSend(
                buildSubscribe("/topic/voice/abc", principal("user@test.com")), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("잘못된 구독 경로");
        verifyNoInteractions(roomMembershipChecker);
    }

    @Test
    void SUBSCRIBE_비보호_토픽_검증없이_통과() {
        Message<?> result = interceptor.preSend(
                buildSubscribe("/topic/notifications/5", principal("user@test.com")), channel);

        assertThat(result).isNotNull();
        verifyNoInteractions(roomMembershipChecker);
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

    private Message<byte[]> buildSubscribe(String destination, Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (principal != null) {
            accessor.setUser(principal);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
