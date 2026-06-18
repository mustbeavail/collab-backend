package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.RoomMember;
import com.groupware.domain.Team;
import com.groupware.domain.TeamMember;
import com.groupware.domain.User;
import com.groupware.dto.team.*;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.TeamRepository;
import com.groupware.repository.UserRepository;
import com.groupware.dto.notification.NotificationPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private com.groupware.websocket.WebSocketEventListener wsEventListener;

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
        given(roomMemberRepository.findJoinedRoomIdxByUserIdAndRoomIds(anyString(), anyList())).willReturn(List.of(1L, 2L));

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
        given(chatRoomRepository.save(any(ChatRoom.class))).willReturn(buildChatRoom(1L, savedTeam, "general"));
        given(roomMemberRepository.save(any(RoomMember.class))).willReturn(new RoomMember());
        given(teamMemberRepository.findActiveByTeamIdx(10L)).willReturn(
                List.of(buildTeamMember(savedTeam, user, "LEADER")));
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(10L)).willReturn(
                List.of(buildChatRoom(1L, savedTeam, "general")));
        given(roomMemberRepository.findJoinedRoomIdxByUserIdAndRoomIds(anyString(), anyList())).willReturn(List.of(1L));

        TeamSidebarResponse result = teamService.createTeam("uid-1", buildCreateRequest("새팀", null));

        assertThat(result.getTeamName()).isEqualTo("새팀");
        assertThat(result.getMyRole()).isEqualTo("LEADER");
        assertThat(result.getChannels()).hasSize(1);
        assertThat(result.getChannels().get(0).getRoomName()).isEqualTo("general");
        assertThat(result.getChannels().get(0).isJoined()).isTrue();
        verify(teamRepository).save(any(Team.class));
        verify(teamMemberRepository).save(any(TeamMember.class));
        verify(chatRoomRepository).save(any(ChatRoom.class));
        verify(roomMemberRepository).save(any(RoomMember.class));
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
        // 실시간 팀초대 알림 WS 전송(qa 항목12)
        verify(messagingTemplate).convertAndSendToUser(
                eq("uid-2"), eq("/queue/notifications"), any(NotificationPayload.class));
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

    // ─── leaveTeam ───────────────────────────────────────────────────────────

    @Test
    void 팀_나가기_MEMBER_성공() {
        Team team = buildTeam(1L, "팀");
        User user = buildUser("uid-1", "멤버");
        TeamMember member = buildTeamMember(team, user, "MEMBER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(member));
        given(teamMemberRepository.save(any(TeamMember.class))).willReturn(member);

        teamService.leaveTeam("uid-1", 1L);

        assertThat(member.getExitAt()).isNotNull();
        verify(teamMemberRepository).save(member);
    }

    @Test
    void 팀_나가기_MANAGER_성공() {
        Team team = buildTeam(1L, "팀");
        User user = buildUser("uid-1", "매니저");
        TeamMember manager = buildTeamMember(team, user, "MANAGER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(manager));
        given(teamMemberRepository.save(any(TeamMember.class))).willReturn(manager);

        teamService.leaveTeam("uid-1", 1L);

        assertThat(manager.getExitAt()).isNotNull();
    }

    @Test
    void 팀_나가기_LEADER_예외() {
        Team team = buildTeam(1L, "팀");
        User user = buildUser("uid-1", "리더");
        TeamMember leader = buildTeamMember(team, user, "LEADER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(leader));

        assertThatThrownBy(() -> teamService.leaveTeam("uid-1", 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.LEADER_CANNOT_LEAVE));
    }

    @Test
    void 팀_나가기_팀없음_예외() {
        given(teamRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.leaveTeam("uid-1", 99L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TEAM_NOT_FOUND));
    }

    @Test
    void 팀_나가기시_팀_채팅방에서_일괄_탈퇴() {
        Team team = buildTeam(1L, "팀");
        User user = buildUser("uid-1", "멤버");
        TeamMember member = buildTeamMember(team, user, "MEMBER");
        ChatRoom room = buildChatRoom(10L, team, "general");
        RoomMember rm = new RoomMember();
        rm.setChatRoom(room);
        rm.setUser(user);

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(member));
        given(teamMemberRepository.save(any(TeamMember.class))).willReturn(member);
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(1L)).willReturn(List.of(room));
        given(roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(Optional.of(rm));

        teamService.leaveTeam("uid-1", 1L);

        assertThat(member.getExitAt()).isNotNull();
        assertThat(rm.getExitAt()).isNotNull();          // 팀 채팅방 멤버십도 정리됨(버그 항목22)
        verify(roomMemberRepository).save(rm);
    }

    // ─── reassignLeadershipForWithdrawnUser (추가2) ───────────────────────────
    @Test
    void 리더_탈퇴_자동위임_매니저_우선() {
        Team team = buildTeam(1L, "팀");
        TeamMember lead = buildTeamMember(team, buildUser("uid-L", "리더"), "LEADER");
        TeamMember mgr = buildTeamMember(team, buildUser("uid-M", "매니저"), "MANAGER");
        mgr.setJoinAt(LocalDateTime.now().minusDays(1));
        TeamMember mem = buildTeamMember(team, buildUser("uid-A", "멤버"), "MEMBER");
        mem.setJoinAt(LocalDateTime.now().minusDays(5)); // 더 먼저 들어왔어도 역할 우선순위가 앞섬

        given(teamMemberRepository.findActiveLeaderMembershipsByUserId("uid-L")).willReturn(List.of(lead));
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(lead, mgr, mem));

        teamService.reassignLeadershipForWithdrawnUser("uid-L");

        assertThat(mgr.getRole()).isEqualTo("LEADER");
        assertThat(lead.getExitAt()).isNotNull();
    }

    @Test
    void 리더_탈퇴_자동위임_동순위는_먼저들어온사람() {
        Team team = buildTeam(1L, "팀");
        TeamMember lead = buildTeamMember(team, buildUser("uid-L", "리더"), "LEADER");
        TeamMember a = buildTeamMember(team, buildUser("uid-A", "A"), "MEMBER");
        a.setJoinAt(LocalDateTime.now().minusDays(2));
        TeamMember b = buildTeamMember(team, buildUser("uid-B", "B"), "MEMBER");
        b.setJoinAt(LocalDateTime.now().minusDays(10)); // 더 먼저 들어옴

        given(teamMemberRepository.findActiveLeaderMembershipsByUserId("uid-L")).willReturn(List.of(lead));
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(lead, a, b));

        teamService.reassignLeadershipForWithdrawnUser("uid-L");

        assertThat(b.getRole()).isEqualTo("LEADER");
        assertThat(a.getRole()).isEqualTo("MEMBER");
    }

    @Test
    void 리더_탈퇴_남은멤버없으면_팀_소프트삭제() {
        Team team = buildTeam(1L, "팀");
        TeamMember lead = buildTeamMember(team, buildUser("uid-L", "리더"), "LEADER");

        given(teamMemberRepository.findActiveLeaderMembershipsByUserId("uid-L")).willReturn(List.of(lead));
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(lead));

        teamService.reassignLeadershipForWithdrawnUser("uid-L");

        assertThat(team.getDelAt()).isNotNull();
        assertThat(lead.getExitAt()).isNotNull();
    }

    // ─── createChannel (qa 항목21) ─────────────────────────────────────────────

    @Test
    void 채널_생성_성공_생성자만_입장() {
        Team team = buildTeam(1L, "팀");
        User user = buildUser("uid-1", "생성자");
        TeamMember member = buildTeamMember(team, user, "MEMBER");
        ChatRoom created = buildChatRoom(20L, team, "새채널");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(member));
        given(chatRoomRepository.save(any(ChatRoom.class))).willReturn(created);
        given(roomMemberRepository.save(any(RoomMember.class))).willReturn(new RoomMember());
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(member));
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(1L)).willReturn(List.of(created));
        given(roomMemberRepository.findJoinedRoomIdxByUserIdAndRoomIds(anyString(), anyList())).willReturn(List.of(20L));

        teamService.createChannel("uid-1", 1L, buildCreateChannelRequest("새채널"));

        // 생성자 RoomMember 1명만 저장(팀원 자동입장 안 함)
        verify(roomMemberRepository, times(1)).save(any(RoomMember.class));
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    void 채널_생성_팀멤버_아님_예외() {
        Team team = buildTeam(1L, "팀");
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(userRepository.findById("uid-x")).willReturn(Optional.of(buildUser("uid-x", "외부인")));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.createChannel("uid-x", 1L, buildCreateChannelRequest("ch")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_TEAM_MEMBER));
    }

    // ─── joinChannel ─────────────────────────────────────────────────────────

    @Test
    void 채널_참여_성공() {
        Team team = buildTeam(1L, "팀");
        User user = buildUser("uid-1", "멤버");
        TeamMember member = buildTeamMember(team, user, "MEMBER");
        ChatRoom chatRoom = buildChatRoom(10L, team, "general");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(member));
        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(chatRoom, user)).willReturn(false);
        given(roomMemberRepository.save(any(RoomMember.class))).willReturn(new RoomMember());
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(member));
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(1L)).willReturn(List.of(chatRoom));
        given(roomMemberRepository.findJoinedRoomIdxByUserIdAndRoomIds(anyString(), anyList())).willReturn(List.of(10L));

        TeamSidebarResponse result = teamService.joinChannel("uid-1", 1L, 10L);

        verify(roomMemberRepository).save(any(RoomMember.class));
        assertThat(result.getChannels().get(0).isJoined()).isTrue();
    }

    @Test
    void 채널_참여_이미_멤버이면_저장_건너뜀() {
        Team team = buildTeam(1L, "팀");
        User user = buildUser("uid-1", "멤버");
        TeamMember member = buildTeamMember(team, user, "MEMBER");
        ChatRoom chatRoom = buildChatRoom(10L, team, "general");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.of(member));
        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(chatRoom, user)).willReturn(true);
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(member));
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(1L)).willReturn(List.of(chatRoom));
        given(roomMemberRepository.findJoinedRoomIdxByUserIdAndRoomIds(anyString(), anyList())).willReturn(List.of(10L));

        teamService.joinChannel("uid-1", 1L, 10L);

        verify(roomMemberRepository, times(0)).save(any(RoomMember.class));
    }

    @Test
    void 채널_참여_팀멤버_아님_예외() {
        Team team = buildTeam(1L, "팀");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(1L, "uid-1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.joinChannel("uid-1", 1L, 10L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_TEAM_MEMBER));
    }

    // ─── getTeamInfo (qa 항목20) + 멤버 정렬(qa 항목18) ────────────────────────

    @Test
    void 팀정보_조회_멤버_성공_그리고_멤버는_리더_매니저_멤버_순_정렬() {
        Team team = buildTeam(1L, "팀");
        TeamMember m1 = buildTeamMember(team, buildUser("u-mem", "멤버"), "MEMBER");
        TeamMember m2 = buildTeamMember(team, buildUser("u-lead", "리더"), "LEADER");
        TeamMember m3 = buildTeamMember(team, buildUser("u-mgr", "매니저"), "MANAGER");

        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findCurrentByTeamIdxAndUserId(1L, "u-lead")).willReturn(Optional.of(m2));
        // 일부러 뒤섞인 순서로 반환 → 서비스가 정렬해야 함
        given(teamMemberRepository.findActiveByTeamIdx(1L)).willReturn(List.of(m1, m2, m3));
        given(chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(1L)).willReturn(List.of());

        TeamSidebarResponse result = teamService.getTeamInfo("u-lead", 1L);

        assertThat(result.getMembers()).extracting(TeamMemberDto::getRole)
                .containsExactly("LEADER", "MANAGER", "MEMBER");
    }

    @Test
    void 팀정보_조회_멤버아님_예외() {
        Team team = buildTeam(1L, "팀");
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findCurrentByTeamIdxAndUserId(1L, "uid-x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeamInfo("uid-x", 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_TEAM_MEMBER));
    }

    @Test
    void 팀정보_조회_팀없음_예외() {
        given(teamRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeamInfo("uid-1", 99L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TEAM_NOT_FOUND));
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

    private CreateChannelRequest buildCreateChannelRequest(String roomName) {
        CreateChannelRequest req = new CreateChannelRequest();
        ReflectionTestUtils.setField(req, "roomName", roomName);
        return req;
    }
}
