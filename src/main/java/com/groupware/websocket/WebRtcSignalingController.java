package com.groupware.websocket;

import com.groupware.dto.voice.SignalingMessage;
import com.groupware.service.VoiceSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class WebRtcSignalingController {

    private final VoiceSessionService voiceSessionService;

    @MessageMapping("/voice.join/{roomIdx}")
    public void join(@DestinationVariable Long roomIdx,
                     @Payload SignalingMessage msg,
                     Principal principal) {
        if (principal == null) return;
        voiceSessionService.join(roomIdx, principal.getName(), msg.getSessionType());
    }

    @MessageMapping("/voice.leave/{roomIdx}")
    public void leave(@DestinationVariable Long roomIdx, Principal principal) {
        if (principal == null) return;
        voiceSessionService.leave(roomIdx, principal.getName());
    }

    // OFFER / ANSWER / ICE 를 특정 사용자에게 유니캐스트
    @MessageMapping("/voice.signal/{roomIdx}")
    public void signal(@DestinationVariable Long roomIdx,
                       @Payload SignalingMessage msg,
                       Principal principal) {
        if (principal == null || msg.getToUserId() == null) return;
        voiceSessionService.signal(roomIdx, principal.getName(), msg);
    }

    // 마이크 토글 → 방 전체 브로드캐스트
    @MessageMapping("/voice.mic/{roomIdx}")
    public void toggleMic(@DestinationVariable Long roomIdx,
                          @Payload SignalingMessage msg,
                          Principal principal) {
        if (principal == null) return;
        voiceSessionService.toggleMic(roomIdx, principal.getName(), Boolean.TRUE.equals(msg.getMicOn()));
    }
}
