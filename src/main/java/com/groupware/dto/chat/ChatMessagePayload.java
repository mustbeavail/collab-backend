package com.groupware.dto.chat;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatMessagePayload {

    private Long msgIdx;
    private Long roomIdx;
    private String userId;
    private String nickname;
    private String avatarUrl;
    private String content;
    private String msgType;
    private LocalDateTime sentAt;
}
