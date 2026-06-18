package com.groupware.websocket;

import com.groupware.dto.notification.NotificationPayload;
import com.groupware.repository.FriendRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final StringRedisTemplate redisTemplate;
    private final FriendRepository friendRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;

    static final String ONLINE_PREFIX = "online:";
    private static final long ONLINE_TTL = 3600L;
    /**
     * 끊김 후 오프라인 확정까지의 유예(초). 페이지 이동/새로고침/순간 네트워크 끊김은
     * 이 시간 안에 재접속되므로 오프라인 처리하지 않는다(버그 항목7: 회원수정 진입 시 비접속 표시 방지).
     */
    private static final long OFFLINE_GRACE_SECONDS = 10L;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-offline-grace");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

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
        // 즉시 오프라인 처리하지 않고 유예 후 재접속 여부를 확인한다.
        // 유예 안에 다른 세션이 살아있으면(=페이지 이동/새로고침으로 재접속) 접속 유지.
        scheduler.schedule(() -> {
            if (!hasActiveSession(userId)) {
                redisTemplate.delete(ONLINE_PREFIX + userId);
                broadcastStatus(userId, "OFFLINE");
            }
        }, OFFLINE_GRACE_SECONDS, TimeUnit.SECONDS);
    }

    private boolean hasActiveSession(String userId) {
        SimpUser u = userRegistry.getUser(userId);
        return u != null && !u.getSessions().isEmpty();
    }

    public boolean isOnline(String userId) {
        // 테스트봇은 실제 WS 접속이 없으므로 항상 온라인으로 취급
        if (com.groupware.service.TestBotService.BOT_USER_ID.equals(userId)) {
            return true;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(ONLINE_PREFIX + userId));
    }

    private void broadcastStatus(String userId, String status) {
        // 수신 대상: 친구 + 같은 팀 동료(중복 제거). 비친구 팀멤버도 실시간 접속표시 받음(버그 항목20)
        Set<String> recipients = new HashSet<>();
        userRepository.findById(userId).ifPresent(user ->
            friendRepository.findAcceptedFriends(user).forEach(f -> {
                String friendUserId = f.getUser().getUserId().equals(userId)
                        ? f.getFriend().getUserId()
                        : f.getUser().getUserId();
                recipients.add(friendUserId);
            })
        );
        recipients.addAll(teamMemberRepository.findTeamCoMemberUserIds(userId));
        recipients.remove(userId);

        NotificationPayload payload = NotificationPayload.builder()
                .type("USER_STATUS")
                .userId(userId)
                .status(status)
                .build();
        recipients.forEach(rid ->
                messagingTemplate.convertAndSendToUser(rid, "/queue/notifications", payload));
    }
}
