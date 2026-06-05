package com.groupware.dto.team;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TeamMemberDto {
    private String userId;
    private String nickname;
    private String role;
}
