package com.groupware.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupware.config.SecurityConfig;
import com.groupware.dto.team.*;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.security.JwtUtil;
import com.groupware.security.UserDetailsServiceImpl;
import com.groupware.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {TeamController.class})
@Import(SecurityConfig.class)
class TeamControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

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
                .channels(List.of(new TeamChannelDto(1L, "general", false), new TeamChannelDto(2L, "random", false)))
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

    // ─── POST /api/teams ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 팀_생성_201() throws Exception {
        TeamSidebarResponse response = TeamSidebarResponse.builder()
                .teamIdx(10L).teamName("새팀").myRole("LEADER")
                .channels(List.of(new TeamChannelDto(1L, "general", false)))
                .members(List.of(new TeamMemberDto("uid-1", "홍길동", "LEADER")))
                .build();

        given(teamService.createTeam(eq("uid-1"), any(CreateTeamRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/teams").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateRequest("새팀", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.teamName").value("새팀"))
                .andExpect(jsonPath("$.data.myRole").value("LEADER"))
                .andExpect(jsonPath("$.data.channels[0].roomName").value("general"));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 팀_생성_이름없음_400() throws Exception {
        mockMvc.perform(post("/api/teams").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 팀_생성_미인증_401() throws Exception {
        mockMvc.perform(post("/api/teams").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateRequest("팀", null))))
                .andExpect(status().isUnauthorized());
    }

    // ─── PUT /api/teams/{teamIdx} ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 팀_수정_200() throws Exception {
        TeamSidebarResponse response = TeamSidebarResponse.builder()
                .teamIdx(1L).teamName("수정된팀").myRole("LEADER")
                .channels(List.of()).members(List.of())
                .build();

        given(teamService.updateTeam(eq("uid-1"), eq(1L), any(UpdateTeamRequest.class))).willReturn(response);

        mockMvc.perform(put("/api/teams/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUpdateRequest("수정된팀", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamName").value("수정된팀"));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 팀_수정_권한없음_403() throws Exception {
        given(teamService.updateTeam(eq("uid-1"), eq(1L), any(UpdateTeamRequest.class)))
                .willThrow(new CustomException(ErrorCode.TEAM_ACCESS_DENIED));

        mockMvc.perform(put("/api/teams/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUpdateRequest("이름", null))))
                .andExpect(status().isForbidden());
    }

    // ─── DELETE /api/teams/{teamIdx} ──────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 팀_삭제_200() throws Exception {
        willDoNothing().given(teamService).deleteTeam("uid-1", 1L);

        mockMvc.perform(delete("/api/teams/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 팀_삭제_권한없음_403() throws Exception {
        willThrow(new CustomException(ErrorCode.TEAM_ACCESS_DENIED))
                .given(teamService).deleteTeam("uid-1", 1L);

        mockMvc.perform(delete("/api/teams/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ─── POST /api/teams/{teamIdx}/invitations ────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 멤버_초대_200() throws Exception {
        willDoNothing().given(teamService).inviteMember(eq("uid-1"), eq(1L), any(InviteTeamMemberRequest.class));

        mockMvc.perform(post("/api/teams/1/invitations").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"uid-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 멤버_초대_자기자신_400() throws Exception {
        willThrow(new CustomException(ErrorCode.CANNOT_INVITE_SELF))
                .given(teamService).inviteMember(eq("uid-1"), eq(1L), any(InviteTeamMemberRequest.class));

        mockMvc.perform(post("/api/teams/1/invitations").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"uid-1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 멤버_초대_이미멤버_409() throws Exception {
        willThrow(new CustomException(ErrorCode.TEAM_ALREADY_MEMBER))
                .given(teamService).inviteMember(eq("uid-1"), eq(1L), any(InviteTeamMemberRequest.class));

        mockMvc.perform(post("/api/teams/1/invitations").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"uid-2\"}"))
                .andExpect(status().isConflict());
    }

    // ─── GET /api/teams/invitations ───────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 내_초대_목록_200() throws Exception {
        TeamInvitationResponse inv = TeamInvitationResponse.builder()
                .tmIdx(5L).teamIdx(1L).teamName("팀A")
                .build();

        given(teamService.getMyInvitations("uid-1")).willReturn(List.of(inv));

        mockMvc.perform(get("/api/teams/invitations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tmIdx").value(5))
                .andExpect(jsonPath("$.data[0].teamName").value("팀A"));
    }

    // ─── PATCH /api/teams/invitations/{tmIdx}/accept ──────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 초대_수락_200() throws Exception {
        TeamSidebarResponse response = TeamSidebarResponse.builder()
                .teamIdx(1L).teamName("팀A").myRole("MEMBER")
                .channels(List.of()).members(List.of())
                .build();

        given(teamService.acceptInvitation("uid-1", 5L)).willReturn(response);

        mockMvc.perform(patch("/api/teams/invitations/5/accept").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamName").value("팀A"));
    }

    // ─── PATCH /api/teams/invitations/{tmIdx}/reject ──────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 초대_거절_200() throws Exception {
        willDoNothing().given(teamService).rejectInvitation("uid-1", 5L);

        mockMvc.perform(patch("/api/teams/invitations/5/reject").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ─── DELETE /api/teams/{teamIdx}/members/{targetUserId} ───────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 멤버_추방_200() throws Exception {
        willDoNothing().given(teamService).kickMember("uid-1", 1L, "uid-2");

        mockMvc.perform(delete("/api/teams/1/members/uid-2").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 멤버_추방_권한없음_403() throws Exception {
        willThrow(new CustomException(ErrorCode.CANNOT_KICK_HIGHER_ROLE))
                .given(teamService).kickMember("uid-1", 1L, "uid-2");

        mockMvc.perform(delete("/api/teams/1/members/uid-2").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ─── PATCH /api/teams/{teamIdx}/members/{targetUserId}/role ──────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 역할_변경_200() throws Exception {
        TeamSidebarResponse response = TeamSidebarResponse.builder()
                .teamIdx(1L).teamName("팀A").myRole("LEADER")
                .channels(List.of()).members(List.of())
                .build();

        given(teamService.changeMemberRole(eq("uid-1"), eq(1L), eq("uid-2"), any(ChangeRoleRequest.class)))
                .willReturn(response);

        mockMvc.perform(patch("/api/teams/1/members/uid-2/role").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRole").value("LEADER"));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 역할_변경_권한없음_403() throws Exception {
        willThrow(new CustomException(ErrorCode.TEAM_ACCESS_DENIED))
                .given(teamService).changeMemberRole(eq("uid-1"), eq(1L), eq("uid-2"), any(ChangeRoleRequest.class));

        mockMvc.perform(patch("/api/teams/1/members/uid-2/role").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isForbidden());
    }

    // ─── DELETE /api/teams/{teamIdx}/leave ────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 팀_나가기_200() throws Exception {
        willDoNothing().given(teamService).leaveTeam("uid-1", 1L);

        mockMvc.perform(delete("/api/teams/1/leave").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 팀_나가기_리더_403() throws Exception {
        willThrow(new CustomException(ErrorCode.LEADER_CANNOT_LEAVE))
                .given(teamService).leaveTeam("uid-1", 1L);

        mockMvc.perform(delete("/api/teams/1/leave").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 팀_나가기_미인증_401() throws Exception {
        mockMvc.perform(delete("/api/teams/1/leave").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /api/teams/{teamIdx}/channels/{roomIdx}/join ────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 채널_참여_200() throws Exception {
        TeamSidebarResponse response = TeamSidebarResponse.builder()
                .teamIdx(1L).teamName("팀A").myRole("MEMBER")
                .channels(List.of(new TeamChannelDto(10L, "general", true))).members(List.of())
                .build();

        given(teamService.joinChannel("uid-1", 1L, 10L)).willReturn(response);

        mockMvc.perform(post("/api/teams/1/channels/10/join").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.channels[0].joined").value(true));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 채널_참여_팀멤버_아님_403() throws Exception {
        willThrow(new CustomException(ErrorCode.NOT_TEAM_MEMBER))
                .given(teamService).joinChannel("uid-1", 1L, 10L);

        mockMvc.perform(post("/api/teams/1/channels/10/join").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CreateTeamRequest buildCreateRequest(String teamName, String about) {
        CreateTeamRequest req = new CreateTeamRequest();
        ReflectionTestUtils.setField(req, "teamName", teamName);
        ReflectionTestUtils.setField(req, "about", about);
        return req;
    }

    private UpdateTeamRequest buildUpdateRequest(String teamName, String about) {
        UpdateTeamRequest req = new UpdateTeamRequest();
        ReflectionTestUtils.setField(req, "teamName", teamName);
        ReflectionTestUtils.setField(req, "about", about);
        return req;
    }
}
