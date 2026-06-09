package com.groupware.dto.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor
public class UpdateRoomInfoRequest {
    @NotBlank
    private String roomName;
}
