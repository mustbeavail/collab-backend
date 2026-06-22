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
        com.groupware.domain.User user = msg.getUser();
        boolean withdrawn = user.getWithdrawalAt() != null;
        return MessageResponse.builder()
                .msgIdx(msg.getMsgIdx())
                .userId(user.getUserId())
                .nickname(withdrawn ? "(탈퇴한 회원)" : user.getNick())
                .avatarUrl(withdrawn ? null : user.getAvatarUrl())
                .content(msg.getContent())
                .msgType(msg.getMsgType())
                .sentAt(msg.getSentAt())
                .build();
    }
}
