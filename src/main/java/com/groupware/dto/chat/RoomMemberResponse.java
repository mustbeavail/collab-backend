package com.groupware.dto.chat;

import com.groupware.domain.RoomMember;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoomMemberResponse {
    private String userId;
    private String nickname;
    private String avatarUrl;
    private String role;

    public static RoomMemberResponse from(RoomMember rm) {
        return RoomMemberResponse.builder()
                .userId(rm.getUser().getUserId())
                .nickname(rm.getUser().getNick())
                .avatarUrl(rm.getUser().getAvatarUrl())
                .role(rm.getRole())
                .build();
    }
}
