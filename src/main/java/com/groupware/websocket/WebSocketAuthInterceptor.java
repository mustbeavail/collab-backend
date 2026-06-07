package com.groupware.websocket;

import com.groupware.domain.ChatRoom;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UserRepository;
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
    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

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
            if (!jwtUtil.validate(token) || jwtUtil.isBlacklisted(token)) {
                throw new MessagingException("유효하지 않은 토큰입니다.");
            }
            String userId = jwtUtil.getUserId(token);
            accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            boolean isRoomTopic = destination != null && destination.startsWith("/topic/room/");
            boolean isDrawTopic = destination != null && destination.startsWith("/topic/draw/");
            if (isRoomTopic || isDrawTopic) {
                Principal principal = accessor.getUser();
                if (principal == null) {
                    throw new MessagingException("인증되지 않은 구독 요청입니다.");
                }
                String userId = principal.getName();
                try {
                    String prefix = isRoomTopic ? "/topic/room/" : "/topic/draw/";
                    Long roomIdx = Long.parseLong(destination.substring(prefix.length()));
                    ChatRoom room = chatRoomRepository.findById(roomIdx)
                            .filter(r -> r.getDelDate() == null)
                            .orElseThrow(() -> new MessagingException("채팅방을 찾을 수 없습니다."));

                    boolean isMember;
                    if (room.getTeam() != null) {
                        isMember = teamMemberRepository
                                .findActiveByTeamIdxAndUserId(room.getTeam().getTeamIdx(), userId)
                                .isPresent();
                    } else {
                        var user = userRepository.findById(userId)
                                .orElseThrow(() -> new MessagingException("사용자를 찾을 수 없습니다."));
                        isMember = roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user);
                    }

                    if (!isMember) {
                        throw new MessagingException("채팅방 멤버가 아닙니다.");
                    }
                } catch (NumberFormatException e) {
                    throw new MessagingException("잘못된 구독 경로입니다.");
                }
            }
        }

        return message;
    }
}
