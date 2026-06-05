package com.groupware.dto.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamInvitationResponse {
    private Long tmIdx;
    private Long teamIdx;
    private String teamName;
}
