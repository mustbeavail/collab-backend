package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Team;
import com.groupware.domain.TeamMember;
import com.groupware.domain.User;
import com.groupware.dto.team.*;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.TeamRepository;
import com.groupware.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @InjectMocks private TeamService teamService;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private UserRepository userRepository;

    @Test
    void 내_팀_목록_정상_반환() {
        Team team = buildTeam(1L, "Team Alpha");
        User leader = buildUser("uid-1", "홍길동");
        User member = buildUser("uid-2", "김영희");
        TeamMember tm1 = buildTeamMember(team, leader, "LEADER");
        TeamMember tm2 = buildTeamMember(team, member, "MEMBER");
        ChatRoom ch1 = buildChatRoom(1L, team, "general");
        ChatRoom ch2 = buildChatRoom(2L, team, "random");

        given(teamRepository.findActiveTeamsByUserId("uid-1")).willReturn(List.of(team));
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(tm1, tm2));
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(1L)).willReturn(List.of(ch1, ch2));

        List<TeamSidebarResponse> result = teamService.getMyTeams("uid-1");

        assertThat(result).hasSize(1);
        TeamSidebarResponse res = result.get(0);
        assertThat(res.getTeamIdx()).isEqualTo(1L);
        assertThat(res.getTeamName()).isEqualTo("Team Alpha");
        assertThat(res.getMyRole()).isEqualTo("LEADER");
        assertThat(res.getChannels()).hasSize(2);
        assertThat(res.getChannels().get(0).getRoomName()).isEqualTo("general");
        assertThat(res.getMembers()).hasSize(2);
        assertThat(res.getMembers().get(0).getNickname()).isEqualTo("홍길동");
    }

    @Test
    void 소속_팀_없을_때_빈_목록_반환() {
        given(teamRepository.findActiveTeamsByUserId("uid-x")).willReturn(List.of());

        List<TeamSidebarResponse> result = teamService.getMyTeams("uid-x");

        assertThat(result).isEmpty();
    }

    @Test
    void 팀에_채팅방_없을_때_빈_채널_목록() {
        Team team = buildTeam(1L, "Empty Team");
        User user = buildUser("uid-1", "테스터");
        TeamMember tm = buildTeamMember(team, user, "MEMBER");

        given(teamRepository.findActiveTeamsByUserId("uid-1")).willReturn(List.of(team));
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(tm));
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(1L)).willReturn(List.of());

        List<TeamSidebarResponse> result = teamService.getMyTeams("uid-1");

        assertThat(result.get(0).getChannels()).isEmpty();
    }

    @Test
    void 내_권한이_멤버목록에_없으면_MEMBER로_기본설정() {
        Team team = buildTeam(1L, "Team X");
        User other = buildUser("uid-2", "다른유저");
        TeamMember tm = buildTeamMember(team, other, "MEMBER");

        given(teamRepository.findActiveTeamsByUserId("uid-1")).willReturn(List.of(team));
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(tm));
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(1L)).willReturn(List.of());

        List<TeamSidebarResponse> result = teamService.getMyTeams("uid-1");

        assertThat(result.get(0).getMyRole()).isEqualTo("MEMBER");
    }

    @Test
    void 여러_팀_소속_시_전체_반환() {
        Team teamA = buildTeam(1L, "Alpha");
        Team teamB = buildTeam(2L, "Beta");
        User user = buildUser("uid-1", "홍길동");
        TeamMember tmA = buildTeamMember(teamA, user, "LEADER");
        TeamMember tmB = buildTeamMember(teamB, user, "MEMBER");

        given(teamRepository.findActiveTeamsByUserId("uid-1")).willReturn(List.of(teamA, teamB));
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(tmA));
        given(teamMemberRepository.findActiveByTeamIdx(2L)).willReturn(List.of(tmB));
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(1L)).willReturn(List.of());
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(2L)).willReturn(List.of());

        List<TeamSidebarResponse> result = teamService.getMyTeams("uid-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTeamName()).isEqualTo("Alpha");
        assertThat(result.get(1).getTeamName()).isEqualTo("Beta");
    }

    // ─── createTeam ──────────────────────────────────────────────────────────

    @Test
    void 팀_생성_성공() {
        User user = buildUser("uid-1", "홍길동");
        Team savedTeam = buildTeam(10L, "새팀");

        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(teamRepository.save(any(Team.class))).willReturn(savedTeam);
        given(teamMemberRepository.save(any(TeamMember.class))).willReturn(new TeamMember());
        given(chatRoomRepository.save(any(ChatRoom.class))).willReturn(new ChatRoom());
        given(teamMemberRepository.findActiveByTeamIdx(10L)).willReturn(
                List.of(buildTeamMember(savedTeam, user, "LEADER")));
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(10L)).willReturn(
                List.of(buildChatRoom(1L, savedTeam, "general")));

        TeamSidebarResponse result = teamService.createTeam("uid-1", buildCreateRequest("새팀", null));

        assertThat(result.getTeamName()).isEqualTo("새팀");
        assertThat(result.getMyRole()).isEqualTo("LEADER");
        assertThat(result.getChannels()).hasSize(1);
        assertThat(result.getChannels().get(0).getRoomName()).isEqualTo("general");
        verify(teamRepository).save(any(Team.class));
        verify(teamMemberRepository).save(any(TeamMember.class));
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    void 팀_생성_사용자없음_예외() {
        given(userRepository.findById("uid-x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.createTeam("uid-x", buildCreateRequest("팀", null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    // ─── updateTeam ──────────────────────────────────────────────────────────

    @Test
    void 팀_수정_MANAGER_성공() {
        Team team = buildTeam(1L, "기존팀명");
        User user = buildUser("uid-1", "홍길동");
        TeamMember manager = buildTeamMember(team, user, "MANAGER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(manager));
        given(teamRepository.save(team)).willReturn(team);
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(manager));
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(1L)).willReturn(List.of());

        TeamSidebarResponse result = teamService.updateTeam("uid-1", 1L, buildUpdateRequest("새팀명", "소개글"));

        assertThat(result.getTeamName()).isEqualTo("새팀명");
        verify(teamRepository).save(team);
    }

    @Test
    void 팀_수정_MEMBER_권한없음_예외() {
        Team team = buildTeam(1L, "팀");
        User user = buildUser("uid-1", "홍길동");
        TeamMember member = buildTeamMember(team, user, "MEMBER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(member));

        assertThatThrownBy(() -> teamService.updateTeam("uid-1", 1L, buildUpdateRequest("새이름", null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TEAM_ACCESS_DENIED));
    }

    @Test
    void 팀_수정_팀없음_예외() {
        given(teamRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.updateTeam("uid-1", 99L, buildUpdateRequest("이름", null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TEAM_NOT_FOUND));
    }

    // ─── deleteTeam ──────────────────────────────────────────────────────────

    @Test
    void 팀_삭제_LEADER_성공() {
        Team team = buildTeam(1L, "삭제할팀");
        User user = buildUser("uid-1", "홍길동");
        TeamMember leader = buildTeamMember(team, user, "LEADER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(leader));
        given(teamRepository.save(team)).willReturn(team);

        teamService.deleteTeam("uid-1", 1L);

        assertThat(team.getDelAt()).isNotNull();
        verify(teamRepository).save(team);
    }

    @Test
    void 팀_삭제_MANAGER_권한없음_예외() {
        Team team = buildTeam(1L, "팀");
        User user = buildUser("uid-1", "홍길동");
        TeamMember manager = buildTeamMember(team, user, "MANAGER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(manager));

        assertThatThrownBy(() -> teamService.deleteTeam("uid-1", 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TEAM_ACCESS_DENIED));
    }

    @Test
    void 팀_삭제_팀없음_예외() {
        given(teamRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.deleteTeam("uid-1", 99L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TEAM_NOT_FOUND));
    }

    // ─── inviteMember ─────────────────────────────────────────────────────────

    @Test
    void 멤버_초대_MANAGER_성공() {
        Team team = buildTeam(1L, "팀");
        User inviter = buildUser("uid-1", "초대자");
        User target = buildUser("uid-2", "초대대상");
        TeamMember myMember = buildTeamMember(team, inviter, "MANAGER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(myMember));
        given(userRepository.findById("uid-2")).willReturn(Optional.of(target));
        given(teamMemberRepository.findCurrentByTeamIdxAndUserId(1L, "uid-2")).willReturn(Optional.empty());
        given(teamMemberRepository.save(any(TeamMember.class))).willReturn(new TeamMember());

        teamService.inviteMember("uid-1", 1L, buildInviteRequest("uid-2"));

        verify(teamMemberRepository).save(any(TeamMember.class));
    }

    @Test
    void 멤버_초대_자기자신_예외() {
        assertThatThrownBy(() -> teamService.inviteMember("uid-1", 1L, buildInviteRequest("uid-1")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CANNOT_INVITE_SELF));
    }

    @Test
    void 멤버_초대_MEMBER_권한없음_예외() {
        Team team = buildTeam(1L, "팀");
        User user = buildUser("uid-1", "멤버");
        TeamMember member = buildTeamMember(team, user, "MEMBER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(member));

        assertThatThrownBy(() -> teamService.inviteMember("uid-1", 1L, buildInviteRequest("uid-2")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TEAM_ACCESS_DENIED));
    }

    @Test
    void 멤버_초대_이미_멤버_예외() {
        Team team = buildTeam(1L, "팀");
        User inviter = buildUser("uid-1", "리더");
        User target = buildUser("uid-2", "이미멤버");
        TeamMember myMember = buildTeamMember(team, inviter, "LEADER");
        TeamMember existing = buildTeamMember(team, target, "MEMBER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(myMember));
        given(userRepository.findById("uid-2")).willReturn(Optional.of(target));
        given(teamMemberRepository.findCurrentByTeamIdxAndUserId(1L, "uid-2")).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> teamService.inviteMember("uid-1", 1L, buildInviteRequest("uid-2")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TEAM_ALREADY_MEMBER));
    }

    // ─── acceptInvitation / rejectInvitation ─────────────────────────────────

    @Test
    void 초대_수락_성공() {
        Team team = buildTeam(1L, "팀");
        User user = buildUser("uid-2", "수락자");
        TeamMember invitation = buildTeamMemberWithStatus(team, user, "MEMBER", "PENDING");
        ReflectionTestUtils.setField(invitation, "tmIdx", 10L);

        given(teamMemberRepository.findById(10L)).willReturn(Optional.of(invitation));
        given(teamMemberRepository.save(any(TeamMember.class))).willReturn(invitation);
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(invitation));
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(1L)).willReturn(List.of());

        TeamSidebarResponse result = teamService.acceptInvitation("uid-2", 10L);

        assertThat(invitation.getStatus()).isEqualTo("ACTIVE");
        assertThat(invitation.getJoinAt()).isNotNull();
        assertThat(result.getTeamIdx()).isEqualTo(1L);
    }

    @Test
    void 초대_수락_권한없음_예외() {
        Team team = buildTeam(1L, "팀");
        User owner = buildUser("uid-2", "초대받은사람");
        TeamMember invitation = buildTeamMemberWithStatus(team, owner, "MEMBER", "PENDING");
        ReflectionTestUtils.setField(invitation, "tmIdx", 10L);

        given(teamMemberRepository.findById(10L)).willReturn(Optional.of(invitation));

        assertThatThrownBy(() -> teamService.acceptInvitation("uid-9", 10L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TEAM_ACCESS_DENIED));
    }

    @Test
    void 초대_거절_성공() {
        Team team = buildTeam(1L, "팀");
        User user = buildUser("uid-2", "거절자");
        TeamMember invitation = buildTeamMemberWithStatus(team, user, "MEMBER", "PENDING");
        ReflectionTestUtils.setField(invitation, "tmIdx", 10L);

        given(teamMemberRepository.findById(10L)).willReturn(Optional.of(invitation));
        given(teamMemberRepository.save(any(TeamMember.class))).willReturn(invitation);

        teamService.rejectInvitation("uid-2", 10L);

        assertThat(invitation.getExitAt()).isNotNull();
    }

    // ─── kickMember ──────────────────────────────────────────────────────────

    @Test
    void 멤버_추방_MANAGER가_MEMBER_추방_성공() {
        Team team = buildTeam(1L, "팀");
        User manager = buildUser("uid-1", "매니저");
        User member = buildUser("uid-2", "일반멤버");
        TeamMember myMember = buildTeamMember(team, manager, "MANAGER");
        TeamMember target = buildTeamMember(team, member, "MEMBER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(myMember));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-2")).willReturn(Optional.of(target));
        given(teamMemberRepository.save(any(TeamMember.class))).willReturn(target);

        teamService.kickMember("uid-1", 1L, "uid-2");

        assertThat(target.getExitAt()).isNotNull();
    }

    @Test
    void 멤버_추방_MANAGER가_LEADER_추방불가_예외() {
        Team team = buildTeam(1L, "팀");
        User manager = buildUser("uid-1", "매니저");
        User leader = buildUser("uid-2", "리더");
        TeamMember myMember = buildTeamMember(team, manager, "MANAGER");
        TeamMember target = buildTeamMember(team, leader, "LEADER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(myMember));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-2")).willReturn(Optional.of(target));

        assertThatThrownBy(() -> teamService.kickMember("uid-1", 1L, "uid-2"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CANNOT_KICK_HIGHER_ROLE));
    }

    @Test
    void 멤버_추방_자기자신_예외() {
        assertThatThrownBy(() -> teamService.kickMember("uid-1", 1L, "uid-1"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TEAM_ACCESS_DENIED));
    }

    // ─── changeMemberRole ────────────────────────────────────────────────────

    @Test
    void 역할_변경_LEADER_성공() {
        Team team = buildTeam(1L, "팀");
        User leader = buildUser("uid-1", "리더");
        User member = buildUser("uid-2", "멤버");
        TeamMember myMember = buildTeamMember(team, leader, "LEADER");
        TeamMember target = buildTeamMember(team, member, "MEMBER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(myMember));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-2")).willReturn(Optional.of(target));
        given(teamMemberRepository.save(any(TeamMember.class))).willReturn(target);
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(myMember, target));
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(1L)).willReturn(List.of());

        teamService.changeMemberRole("uid-1", 1L, "uid-2", buildChangeRoleRequest("MANAGER"));

        assertThat(target.getRole()).isEqualTo("MANAGER");
    }

    @Test
    void 역할_변경_MANAGER_권한없음_예외() {
        Team team = buildTeam(1L, "팀");
        User manager = buildUser("uid-1", "매니저");
        TeamMember myMember = buildTeamMember(team, manager, "MANAGER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(myMember));

        assertThatThrownBy(() -> teamService.changeMemberRole("uid-1", 1L, "uid-2", buildChangeRoleRequest("MEMBER")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TEAM_ACCESS_DENIED));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Team buildTeam(Long idx, String name) {
        Team t = new Team();
        t.setTeamIdx(idx);
        t.setTeamName(name);
        return t;
    }

    private User buildUser(String userId, String nick) {
        User u = new User();
        u.setUserId(userId);
        u.setNick(nick);
        return u;
    }

    private TeamMember buildTeamMember(Team team, User user, String role) {
        TeamMember tm = new TeamMember();
        tm.setTeam(team);
        tm.setUser(user);
        tm.setRole(role);
        return tm;
    }

    private TeamMember buildTeamMemberWithStatus(Team team, User user, String role, String status) {
        TeamMember tm = buildTeamMember(team, user, role);
        tm.setStatus(status);
        return tm;
    }

    private ChatRoom buildChatRoom(Long idx, Team team, String name) {
        ChatRoom cr = new ChatRoom();
        cr.setRoomIdx(idx);
        cr.setTeam(team);
        cr.setRoomName(name);
        return cr;
    }

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

    private InviteTeamMemberRequest buildInviteRequest(String targetUserId) {
        InviteTeamMemberRequest req = new InviteTeamMemberRequest();
        ReflectionTestUtils.setField(req, "targetUserId", targetUserId);
        return req;
    }

    private ChangeRoleRequest buildChangeRoleRequest(String role) {
        ChangeRoleRequest req = new ChangeRoleRequest();
        ReflectionTestUtils.setField(req, "role", role);
        return req;
    }
}
