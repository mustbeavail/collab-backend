package com.groupware.websocket;

import com.groupware.dto.chart.ChartSharePayload;
import com.groupware.domain.User;
import com.groupware.repository.UserRepository;
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

    @MessageMapping("/chart.share/{roomIdx}")
    public void shareChart(@DestinationVariable Long roomIdx,
                           ChartSharePayload payload,
                           Principal principal) {
        if (principal == null) return;
        String userId = principal.getName();
        String nickname = userRepository.findById(userId)
                .map(User::getNick)
                .orElse(userId);
        payload.setFromUserId(userId);
        payload.setFromNickname(nickname);
        messagingTemplate.convertAndSend("/topic/chart/" + roomIdx, payload);
    }
}
