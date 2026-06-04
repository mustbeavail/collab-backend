package com.groupware.dto.friend;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class FriendRequestDto {

    @NotBlank(message = "대상 사용자 ID는 필수입니다.")
    private String targetUserId;
}
