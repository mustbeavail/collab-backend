package com.groupware.dto.friend;

import com.groupware.domain.Friend;
import com.groupware.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FriendResponse {

    private Long friendIdx;
    private String userId;
    private String nickname;
    private String email;
    private String status;
    private String avatarUrl;

    public static FriendResponse of(Friend f, User me) {
        User other = f.getUser().getUserId().equals(me.getUserId()) ? f.getFriend() : f.getUser();
        return FriendResponse.builder()
                .friendIdx(f.getFriendIdx())
                .userId(other.getUserId())
                .nickname(other.getNick() != null ? other.getNick() : "(탈퇴한 회원)")
                .email(other.getUserId())
                .status(f.getStatus())
                .avatarUrl(other.getAvatarUrl())
                .build();
    }
}
