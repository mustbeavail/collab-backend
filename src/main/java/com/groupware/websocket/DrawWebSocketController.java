package com.groupware.websocket;

import com.groupware.dto.draw.DrawEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class DrawWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/draw.event/{roomIdx}")
    public void drawEvent(@DestinationVariable Long roomIdx,
                          DrawEventPayload payload,
                          Principal principal) {
        if (principal == null) return;
        messagingTemplate.convertAndSend("/topic/draw/" + roomIdx, payload);
    }
}
