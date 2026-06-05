package com.groupware.dto.team;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TeamSidebarResponse {
    private Long teamIdx;
    private String teamName;
    private String about;
    private String myRole;
    private List<TeamChannelDto> channels;
    private List<TeamMemberDto> members;
}
