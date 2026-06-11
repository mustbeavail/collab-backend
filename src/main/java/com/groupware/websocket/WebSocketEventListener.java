package com.groupware.websocket;

import com.groupware.dto.notification.NotificationPayload;
import com.groupware.repository.FriendRepository;
import com.groupware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final StringRedisTemplate redisTemplate;
    private final FriendRepository friendRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    static final String ONLINE_PREFIX = "online:";
    private static final long ONLINE_TTL = 3600L;

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal == null) return;

        String userId = principal.getName();
        redisTemplate.opsForValue().set(ONLINE_PREFIX + userId, "1", ONLINE_TTL, TimeUnit.SECONDS);
        broadcastStatus(userId, "ONLINE");
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal == null) return;

        String userId = principal.getName();
        redisTemplate.delete(ONLINE_PREFIX + userId);
        broadcastStatus(userId, "OFFLINE");
    }

    public boolean isOnline(String userId) {
        // 테스트봇은 실제 WS 접속이 없으므로 항상 온라인으로 취급
        if (com.groupware.service.TestBotService.BOT_USER_ID.equals(userId)) {
            return true;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(ONLINE_PREFIX + userId));
    }

    private void broadcastStatus(String userId, String status) {
        userRepository.findById(userId).ifPresent(user ->
            friendRepository.findAcceptedFriends(user).forEach(f -> {
                String friendUserId = f.getUser().getUserId().equals(userId)
                        ? f.getFriend().getUserId()
                        : f.getUser().getUserId();
                messagingTemplate.convertAndSendToUser(
                        friendUserId, "/queue/notifications",
                        NotificationPayload.builder()
                                .type("USER_STATUS")
                                .userId(userId)
                                .status(status)
                                .build()
                );
            })
        );
    }
}
