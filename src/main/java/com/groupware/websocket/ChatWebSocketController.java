package com.groupware.websocket;

import com.groupware.dto.chat.SendMessageRequest;
import com.groupware.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;

    @MessageMapping("/chat.send/{roomIdx}")
    public void sendMessage(@DestinationVariable Long roomIdx,
                            SendMessageRequest request,
                            Principal principal) {
        if (principal == null) return;
        chatService.sendMessage(principal.getName(), roomIdx, request);
    }
}
