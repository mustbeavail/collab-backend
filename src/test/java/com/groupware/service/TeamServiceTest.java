package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Team;
import com.groupware.domain.TeamMember;
import com.groupware.domain.User;
import com.groupware.dto.team.TeamSidebarResponse;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @InjectMocks private TeamService teamService;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private ChatRoomRepository chatRoomRepository;

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

    private ChatRoom buildChatRoom(Long idx, Team team, String name) {
        ChatRoom cr = new ChatRoom();
        cr.setRoomIdx(idx);
        cr.setTeam(team);
        cr.setRoomName(name);
        return cr;
    }
}
