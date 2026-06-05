package com.groupware.dto.team;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InviteTeamMemberRequest {

    @NotBlank
    private String targetUserId;
}
