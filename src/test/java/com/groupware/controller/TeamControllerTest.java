package com.groupware.controller;

import com.groupware.config.SecurityConfig;
import com.groupware.dto.team.TeamChannelDto;
import com.groupware.dto.team.TeamMemberDto;
import com.groupware.dto.team.TeamSidebarResponse;
import com.groupware.security.JwtUtil;
import com.groupware.security.UserDetailsServiceImpl;
import com.groupware.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {TeamController.class})
@Import(SecurityConfig.class)
class TeamControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private TeamService teamService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    @Test
    @WithMockUser(username = "uid-1")
    void 내_팀_목록_조회_200() throws Exception {
        TeamSidebarResponse response = TeamSidebarResponse.builder()
                .teamIdx(1L)
                .teamName("Team Alpha")
                .myRole("LEADER")
                .channels(List.of(new TeamChannelDto(1L, "general"), new TeamChannelDto(2L, "random")))
                .members(List.of(new TeamMemberDto("uid-1", "홍길동", "LEADER")))
                .build();

        given(teamService.getMyTeams("uid-1")).willReturn(List.of(response));

        mockMvc.perform(get("/api/teams/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].teamIdx").value(1))
                .andExpect(jsonPath("$.data[0].teamName").value("Team Alpha"))
                .andExpect(jsonPath("$.data[0].myRole").value("LEADER"))
                .andExpect(jsonPath("$.data[0].channels[0].roomName").value("general"))
                .andExpect(jsonPath("$.data[0].members[0].nickname").value("홍길동"));
    }

    @Test
    void 내_팀_목록_미인증_401() throws Exception {
        mockMvc.perform(get("/api/teams/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 소속_팀_없을_때_빈_배열_200() throws Exception {
        given(teamService.getMyTeams("uid-1")).willReturn(List.of());

        mockMvc.perform(get("/api/teams/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
