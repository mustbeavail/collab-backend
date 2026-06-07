package com.groupware.websocket;

import com.groupware.dto.draw.DrawEventPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DrawWebSocketControllerTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    DrawWebSocketController controller;

    @Test
    void drawEvent_DRAW_MOVE_발행시_해당_토픽으로_브로드캐스트() {
        var payload = new DrawEventPayload();
        payload.setEventType("DRAW_MOVE");
        payload.setUserId("user1");
        payload.setNickname("홍길동");
        Principal principal = new UsernamePasswordAuthenticationToken("user1", null);

        controller.drawEvent(42L, payload, principal);

        then(messagingTemplate).should().convertAndSend("/topic/draw/42", payload);
    }

    @Test
    void drawEvent_DRAW_DONE_발행시_해당_토픽으로_브로드캐스트() {
        var el = new DrawEventPayload.DrawElement();
        el.setId("el-1");
        el.setType("pencil");
        var payload = new DrawEventPayload();
        payload.setEventType("DRAW_DONE");
        payload.setUserId("user2");
        payload.setElement(el);
        Principal principal = new UsernamePasswordAuthenticationToken("user2", null);

        controller.drawEvent(7L, payload, principal);

        then(messagingTemplate).should().convertAndSend("/topic/draw/7", payload);
    }

    @Test
    void drawEvent_DRAW_CLEAR_발행시_해당_토픽으로_브로드캐스트() {
        var payload = new DrawEventPayload();
        payload.setEventType("DRAW_CLEAR");
        payload.setUserId("user3");
        Principal principal = new UsernamePasswordAuthenticationToken("user3", null);

        controller.drawEvent(1L, payload, principal);

        then(messagingTemplate).should().convertAndSend("/topic/draw/1", payload);
    }

    @Test
    void drawEvent_DRAW_UNDO_발행시_해당_토픽으로_브로드캐스트() {
        var payload = new DrawEventPayload();
        payload.setEventType("DRAW_UNDO");
        payload.setUserId("user1");
        payload.setElementId("el-99");
        Principal principal = new UsernamePasswordAuthenticationToken("user1", null);

        controller.drawEvent(3L, payload, principal);

        then(messagingTemplate).should().convertAndSend("/topic/draw/3", payload);
    }

    @Test
    void drawEvent_principal_null이면_브로드캐스트_안함() {
        controller.drawEvent(1L, new DrawEventPayload(), null);

        verifyNoInteractions(messagingTemplate);
    }
}
