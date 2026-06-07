package com.groupware.websocket;

import com.groupware.dto.voice.SignalingMessage;
import com.groupware.service.VoiceSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebRtcSignalingControllerTest {

    @InjectMocks private WebRtcSignalingController controller;
    @Mock private VoiceSessionService voiceSessionService;

    private Principal principal(String name) { return () -> name; }

    // ── join ──────────────────────────────────────────

    @Test
    void join_delegates_to_service_with_sessionType() {
        SignalingMessage msg = new SignalingMessage();
        msg.setSessionType("VIDEO");

        controller.join(1L, msg, principal("user@test.com"));

        verify(voiceSessionService).join(1L, "user@test.com", "VIDEO");
    }

    @Test
    void join_ignores_null_principal() {
        controller.join(1L, new SignalingMessage(), null);
        verifyNoInteractions(voiceSessionService);
    }

    // ── leave ─────────────────────────────────────────

    @Test
    void leave_delegates_to_service() {
        controller.leave(1L, principal("user@test.com"));
        verify(voiceSessionService).leave(1L, "user@test.com");
    }

    @Test
    void leave_ignores_null_principal() {
        controller.leave(1L, null);
        verifyNoInteractions(voiceSessionService);
    }

    // ── signal ────────────────────────────────────────

    @Test
    void signal_delegates_to_service_when_toUserId_present() {
        SignalingMessage msg = new SignalingMessage();
        msg.setType("OFFER");
        msg.setToUserId("target@test.com");

        controller.signal(1L, msg, principal("user@test.com"));

        verify(voiceSessionService).signal(eq(1L), eq("user@test.com"), any());
    }

    @Test
    void signal_ignores_null_toUserId() {
        SignalingMessage msg = new SignalingMessage();
        msg.setType("OFFER");
        // toUserId is null

        controller.signal(1L, msg, principal("user@test.com"));

        verifyNoInteractions(voiceSessionService);
    }

    @Test
    void signal_ignores_null_principal() {
        SignalingMessage msg = new SignalingMessage();
        msg.setToUserId("target@test.com");

        controller.signal(1L, msg, null);

        verifyNoInteractions(voiceSessionService);
    }

    // ── toggleMic ─────────────────────────────────────

    @Test
    void toggleMic_delegates_micOn_true_to_service() {
        SignalingMessage msg = new SignalingMessage();
        msg.setMicOn(true);

        controller.toggleMic(1L, msg, principal("user@test.com"));

        verify(voiceSessionService).toggleMic(1L, "user@test.com", true);
    }

    @Test
    void toggleMic_delegates_micOn_false_to_service() {
        SignalingMessage msg = new SignalingMessage();
        msg.setMicOn(false);

        controller.toggleMic(1L, msg, principal("user@test.com"));

        verify(voiceSessionService).toggleMic(1L, "user@test.com", false);
    }

    @Test
    void toggleMic_treats_null_micOn_as_false() {
        SignalingMessage msg = new SignalingMessage();
        // micOn is null

        controller.toggleMic(1L, msg, principal("user@test.com"));

        verify(voiceSessionService).toggleMic(1L, "user@test.com", false);
    }

    @Test
    void toggleMic_ignores_null_principal() {
        controller.toggleMic(1L, new SignalingMessage(), null);
        verifyNoInteractions(voiceSessionService);
    }
}
