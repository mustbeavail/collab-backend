package com.groupware.dto.user;

import com.groupware.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserProfileResponse {

    private String userId;
    private String email;
    private String nickname;
    private String about;
    private String avatarUrl;
    private LocalDateTime joinAt;

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .email(user.getMailAdr())
                .nickname(user.getNick())
                .about(user.getAbout())
                .avatarUrl(user.getAvatarUrl())
                .joinAt(user.getJoinAt())
                .build();
    }
}
