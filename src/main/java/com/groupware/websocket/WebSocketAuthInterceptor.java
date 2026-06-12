package com.groupware.websocket;

import com.groupware.service.RoomMembershipChecker;
import com.groupware.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final RoomMembershipChecker roomMembershipChecker;

    /** 구독 시 방 멤버십 검증이 필요한 토픽 prefix 목록. 모두 /prefix/{roomIdx} 형태. */
    private static final List<String> MEMBERSHIP_GUARDED_PREFIXES = List.of(
            "/topic/room/",
            "/topic/draw/",
            "/topic/voice/",
            "/topic/chart/"
    );

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new MessagingException("인증 토큰이 없습니다.");
            }
            String token = authHeader.substring(7);
            if (!jwtUtil.validate(token) || jwtUtil.isBlacklisted(token) || !jwtUtil.isCurrentSession(token)) {
                throw new MessagingException("유효하지 않은 토큰입니다.");
            }
            String userId = jwtUtil.getUserId(token);
            accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            String prefix = guardedPrefix(destination);
            if (prefix != null) {
                Principal principal = accessor.getUser();
                if (principal == null) {
                    throw new MessagingException("인증되지 않은 구독 요청입니다.");
                }
                try {
                    Long roomIdx = Long.parseLong(destination.substring(prefix.length()));
                    if (!roomMembershipChecker.isMember(roomIdx, principal.getName())) {
                        throw new MessagingException("구독 권한이 없습니다.");
                    }
                } catch (NumberFormatException e) {
                    throw new MessagingException("잘못된 구독 경로입니다.");
                }
            }
        }

        return message;
    }

    private String guardedPrefix(String destination) {
        if (destination == null) return null;
        return MEMBERSHIP_GUARDED_PREFIXES.stream()
                .filter(destination::startsWith)
                .findFirst()
                .orElse(null);
    }
}
