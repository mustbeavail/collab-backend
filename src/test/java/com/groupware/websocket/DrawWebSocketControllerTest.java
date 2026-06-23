package com.groupware.websocket;

import com.groupware.dto.draw.DrawEventPayload;
import com.groupware.service.DrawStateStore;
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

    @Mock
    DrawStateStore drawStateStore;

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
        verifyNoInteractions(drawStateStore);
    }

    // ── 항목8(일정이후): 서버측 캔버스 상태 반영 ──────────────────────────────

    @Test
    void drawEvent_DRAW_DONE이면_상태저장소에_append() {
        var el = new DrawEventPayload.DrawElement();
        el.setId("el-1");
        el.setType("pencil");
        var payload = new DrawEventPayload();
        payload.setEventType("DRAW_DONE");
        payload.setUserId("user1");
        payload.setElement(el);
        Principal principal = new UsernamePasswordAuthenticationToken("user1", null);

        controller.drawEvent(7L, payload, principal);

        then(drawStateStore).should().append(7L, el);
        then(messagingTemplate).should().convertAndSend("/topic/draw/7", payload);
    }

    @Test
    void drawEvent_DRAW_UNDO이면_상태저장소에서_remove() {
        var payload = new DrawEventPayload();
        payload.setEventType("DRAW_UNDO");
        payload.setUserId("user1");
        payload.setElementId("el-99");
        Principal principal = new UsernamePasswordAuthenticationToken("user1", null);

        controller.drawEvent(3L, payload, principal);

        then(drawStateStore).should().remove(3L, "el-99");
        then(messagingTemplate).should().convertAndSend("/topic/draw/3", payload);
    }

    @Test
    void drawEvent_DRAW_CLEAR이면_상태저장소_clear() {
        var payload = new DrawEventPayload();
        payload.setEventType("DRAW_CLEAR");
        payload.setUserId("user1");
        Principal principal = new UsernamePasswordAuthenticationToken("user1", null);

        controller.drawEvent(5L, payload, principal);

        then(drawStateStore).should().clear(5L);
        then(messagingTemplate).should().convertAndSend("/topic/draw/5", payload);
    }

    @Test
    void drawEvent_DRAW_MOVE는_상태저장소_미반영() {
        var el = new DrawEventPayload.DrawElement();
        el.setId("el-move");
        var payload = new DrawEventPayload();
        payload.setEventType("DRAW_MOVE");
        payload.setUserId("user1");
        payload.setElement(el);
        Principal principal = new UsernamePasswordAuthenticationToken("user1", null);

        controller.drawEvent(9L, payload, principal);

        verifyNoInteractions(drawStateStore);
        then(messagingTemplate).should().convertAndSend("/topic/draw/9", payload);
    }
}
