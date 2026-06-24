package com.groupware.websocket;

import com.groupware.dto.chart.ChartSharePayload;
import com.groupware.domain.User;
import com.groupware.repository.UserRepository;
import com.groupware.service.ChartStateStore;
import com.groupware.service.RoomMembershipChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChartWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final RoomMembershipChecker roomMembershipChecker;
    private final ChartStateStore chartStateStore;

    @MessageMapping("/chart.share/{roomIdx}")
    public void shareChart(@DestinationVariable Long roomIdx,
                           ChartSharePayload payload,
                           Principal principal) {
        if (principal == null) return;
        String userId = principal.getName();
        roomMembershipChecker.check(roomIdx, userId);
        String nickname = userRepository.findById(userId)
                .map(User::getNick)
                .orElse(userId);
        payload.setFromUserId(userId);
        payload.setFromNickname(nickname);
        // 항목4(일정이후): 나중에 패널을 여는 사용자도 현재 차트를 보도록 서버에 최신 차트 보관.
        chartStateStore.put(roomIdx, payload);
        messagingTemplate.convertAndSend("/topic/chart/" + roomIdx, payload);
    }
}
