package com.groupware.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangeRoleRequest {

    @NotBlank
    @Pattern(regexp = "LEADER|MANAGER|MEMBER", message = "역할은 LEADER, MANAGER, MEMBER 중 하나여야 합니다.")
    private String role;
}
