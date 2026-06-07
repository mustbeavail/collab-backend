package com.groupware.dto.voice;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class SignalingMessage {
    private String type;         // JOIN, LEAVE, OFFER, ANSWER, ICE, MIC_TOGGLE
    private String fromUserId;
    private String fromNickname;
    private String toUserId;     // OFFER, ANSWER, ICE 전용
    private String sessionType;  // VOICE, VIDEO (JOIN 전용)
    private String sdp;          // OFFER, ANSWER 전용
    private String candidate;    // ICE 전용 (RTCIceCandidate JSON 문자열)
    private Boolean micOn;       // MIC_TOGGLE 전용
}
