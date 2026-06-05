package com.groupware.dto.chat;

import com.groupware.domain.Message;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MessageResponse {

    private Long msgIdx;
    private String userId;
    private String nickname;
    private String avatarUrl;
    private String content;
    private String msgType;
    private LocalDateTime sentAt;

    public static MessageResponse from(Message msg) {
        return MessageResponse.builder()
                .msgIdx(msg.getMsgIdx())
                .userId(msg.getUser().getUserId())
                .nickname(msg.getUser().getNick())
                .avatarUrl(msg.getUser().getAvatarUrl())
                .content(msg.getContent())
                .msgType(msg.getMsgType())
                .sentAt(msg.getSentAt())
                .build();
    }
}
