package com.groupware.dto.user;

import com.groupware.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserSearchResponse {

    private String userId;
    private String nickname;
    private String email;

    public static UserSearchResponse from(User user) {
        return UserSearchResponse.builder()
                .userId(user.getUserId())
                .nickname(user.getNick())
                .email(user.getUserId())
                .build();
    }
}
