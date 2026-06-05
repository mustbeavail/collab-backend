package com.groupware.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateTeamRequest {

    @NotBlank(message = "팀 이름은 필수입니다.")
    @Size(max = 255, message = "팀 이름은 255자 이하여야 합니다.")
    private String teamName;

    @Size(max = 6000, message = "소개는 6000자 이하여야 합니다.")
    private String about;
}
