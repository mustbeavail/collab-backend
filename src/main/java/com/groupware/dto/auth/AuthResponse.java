package com.groupware.dto.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String userId;
    private String nickname;
    private String email;

    public AuthResponse withoutRefreshToken() {
        return AuthResponse.builder()
                .accessToken(this.accessToken)
                .userId(this.userId)
                .nickname(this.nickname)
                .email(this.email)
                .build();
    }
}
