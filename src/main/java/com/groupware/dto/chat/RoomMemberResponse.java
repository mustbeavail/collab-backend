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
        String nick = rm.getUser().getNick();
        return RoomMemberResponse.builder()
                .userId(rm.getUser().getUserId())
                .nickname(nick != null ? nick : "(탈퇴한 회원)")
                .avatarUrl(rm.getUser().getAvatarUrl())
                .role(rm.getRole())
                .build();
    }
}
