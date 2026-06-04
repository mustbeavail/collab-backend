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

    public static FriendResponse of(Friend f, User me) {
        User other = f.getUser().getUserId().equals(me.getUserId()) ? f.getFriend() : f.getUser();
        return FriendResponse.builder()
                .friendIdx(f.getFriendIdx())
                .userId(other.getUserId())
                .nickname(other.getNick())
                .email(other.getMailAdr())
                .status(f.getStatus())
                .build();
    }
}
