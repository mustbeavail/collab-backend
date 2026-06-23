package com.groupware.websocket;

import com.groupware.dto.draw.DrawEventPayload;
import com.groupware.service.DrawStateStore;
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
    private final DrawStateStore drawStateStore;

    @MessageMapping("/draw.event/{roomIdx}")
    public void drawEvent(@DestinationVariable Long roomIdx,
                          DrawEventPayload payload,
                          Principal principal) {
        if (principal == null) return;

        // 항목8(일정이후): 서버측 캔버스 상태 갱신 — 패널을 닫았다 (다시) 연 사용자도 이전 드로잉을 보도록.
        String eventType = payload.getEventType();
        if ("DRAW_DONE".equals(eventType)) {
            drawStateStore.append(roomIdx, payload.getElement());
        } else if ("DRAW_UNDO".equals(eventType)) {
            drawStateStore.remove(roomIdx, payload.getElementId());
        } else if ("DRAW_CLEAR".equals(eventType)) {
            drawStateStore.clear(roomIdx);
        }
        // DRAW_MOVE(진행중)는 저장하지 않고 릴레이만.

        messagingTemplate.convertAndSend("/topic/draw/" + roomIdx, payload);
    }
}
