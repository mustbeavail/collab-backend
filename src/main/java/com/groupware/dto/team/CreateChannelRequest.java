package com.groupware.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateChannelRequest {

    @NotBlank(message = "채널 이름은 필수입니다.")
    @Size(max = 50, message = "채널 이름은 50자 이하여야 합니다.")
    private String roomName;
}
