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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final RoomMemberRepository roomMemberRepository;

    @Transactional(readOnly = true)
    public List<TeamSidebarResponse> getMyTeams(String userId) {
        List<Team> teams = teamRepository.findActiveTeamsByUserId(userId);
        return teams.stream().map(team -> toSidebarResponse(team, userId)).toList();
    }

    @Transactional
    public TeamSidebarResponse createTeam(String userId, CreateTeamRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Team team = new Team();
        team.setTeamName(request.getTeamName());
        team.setAbout(request.getAbout());
        team.setCreatedAt(LocalDateTime.now());
        team = teamRepository.save(team);

        TeamMember leader = new TeamMember();
        leader.setTeam(team);
        leader.setUser(user);
        leader.setRole("LEADER");
        leader.setStatus("ACTIVE");
        leader.setJoinAt(LocalDateTime.now());
        teamMemberRepository.save(leader);

        ChatRoom general = new ChatRoom();
        general.setTeam(team);
        general.setRoomName("general");
        general.setCreatedAt(LocalDateTime.now());
        general = chatRoomRepository.save(general);

        RoomMember creatorRoomMember = new RoomMember();
        creatorRoomMember.setChatRoom(general);
        creatorRoomMember.setUser(user);
        creatorRoomMember.setRole("MEMBER");
        creatorRoomMember.setJoinAt(LocalDateTime.now());
        roomMemberRepository.save(creatorRoomMember);

        return toSidebarResponse(team, userId);
    }

    @Transactional
    public TeamSidebarResponse updateTeam(String userId, Long teamIdx, UpdateTeamRequest request) {
        Team team = teamRepository.findById(teamIdx)
                .filter(t -> t.getDelAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));

        TeamMember member = teamMemberRepository.findActiveByTeamIdxAndUserId(teamIdx, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));

        if ("MEMBER".equals(member.getRole())) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        team.setTeamName(request.getTeamName());
        team.setAbout(request.getAbout());
        teamRepository.save(team);

        return toSidebarResponse(team, userId);
    }

    @Transactional
    public void deleteTeam(String userId, Long teamIdx) {
        Team team = teamRepository.findById(teamIdx)
                .filter(t -> t.getDelAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));

        TeamMember member = teamMemberRepository.findActiveByTeamIdxAndUserId(teamIdx, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));

        if (!"LEADER".equals(member.getRole())) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        team.setDelAt(LocalDateTime.now());
        teamRepository.save(team);
    }

    // ─── 멤버 초대 ──────────────────────────────────────────────────────────────

    @Transactional
    public void inviteMember(String userId, Long teamIdx, InviteTeamMemberRequest request) {
        String targetUserId = request.getTargetUserId();

        if (userId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.CANNOT_INVITE_SELF);
        }

        Team team = teamRepository.findById(teamIdx)
                .filter(t -> t.getDelAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));

        TeamMember myMember = teamMemberRepository.findActiveByTeamIdxAndUserId(teamIdx, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));

        if ("MEMBER".equals(myMember.getRole())) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 이미 활성 멤버이거나 대기 중인 초대가 있으면 불가
        if (teamMemberRepository.findCurrentByTeamIdxAndUserId(teamIdx, targetUserId).isPresent()) {
            throw new CustomException(ErrorCode.TEAM_ALREADY_MEMBER);
        }

        TeamMember invitation = new TeamMember();
        invitation.setTeam(team);
        invitation.setUser(target);
        invitation.setRole("MEMBER");
        invitation.setStatus("PENDING");
        teamMemberRepository.save(invitation);
    }

    @Transactional(readOnly = true)
    public List<TeamInvitationResponse> getMyInvitations(String userId) {
        return teamMemberRepository.findPendingInvitationsByUserId(userId).stream()
                .map(tm -> TeamInvitationResponse.builder()
                        .tmIdx(tm.getTmIdx())
                        .teamIdx(tm.getTeam().getTeamIdx())
                        .teamName(tm.getTeam().getTeamName())
                        .build())
                .toList();
    }

    @Transactional
    public TeamSidebarResponse acceptInvitation(String userId, Long tmIdx) {
        TeamMember invitation = teamMemberRepository.findById(tmIdx)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_INVITATION_NOT_FOUND));

        if (!invitation.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }
        if (!"PENDING".equals(invitation.getStatus())) {
            throw new CustomException(ErrorCode.TEAM_INVITATION_NOT_FOUND);
        }

        invitation.setStatus("ACTIVE");
        invitation.setJoinAt(LocalDateTime.now());
        teamMemberRepository.save(invitation);

        return toSidebarResponse(invitation.getTeam(), userId);
    }

    @Transactional
    public void rejectInvitation(String userId, Long tmIdx) {
        TeamMember invitation = teamMemberRepository.findById(tmIdx)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_INVITATION_NOT_FOUND));

        if (!invitation.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }
        if (!"PENDING".equals(invitation.getStatus())) {
            throw new CustomException(ErrorCode.TEAM_INVITATION_NOT_FOUND);
        }

        invitation.setExitAt(LocalDateTime.now());
        teamMemberRepository.save(invitation);
    }

    // ─── 멤버 추방 ──────────────────────────────────────────────────────────────

    @Transactional
    public void kickMember(String userId, Long teamIdx, String targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        teamRepository.findById(teamIdx)
                .filter(t -> t.getDelAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));

        TeamMember myMember = teamMemberRepository.findActiveByTeamIdxAndUserId(teamIdx, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));

        if ("MEMBER".equals(myMember.getRole())) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        TeamMember target = teamMemberRepository.findActiveByTeamIdxAndUserId(teamIdx, targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));

        if (rolePriority(myMember.getRole()) <= rolePriority(target.getRole())) {
            throw new CustomException(ErrorCode.CANNOT_KICK_HIGHER_ROLE);
        }

        target.setExitAt(LocalDateTime.now());
        teamMemberRepository.save(target);
    }

    // ─── 역할 변경 ──────────────────────────────────────────────────────────────

    @Transactional
    public TeamSidebarResponse changeMemberRole(String userId, Long teamIdx, String targetUserId, ChangeRoleRequest request) {
        Team team = teamRepository.findById(teamIdx)
                .filter(t -> t.getDelAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));

        TeamMember myMember = teamMemberRepository.findActiveByTeamIdxAndUserId(teamIdx, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));

        if (!"LEADER".equals(myMember.getRole())) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        if (userId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.LEADER_SELF_DEMOTION);
        }

        TeamMember target = teamMemberRepository.findActiveByTeamIdxAndUserId(teamIdx, targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));

        if ("LEADER".equals(request.getRole())) {
            // 리더 위임: 기존 리더를 MANAGER로 강등 후 새 리더 지정
            myMember.setRole("MANAGER");
            teamMemberRepository.save(myMember);
        }

        target.setRole(request.getRole());
        teamMemberRepository.save(target);

        return toSidebarResponse(team, userId);
    }

    // ─── 팀 나가기 ────────────────────────────────────────────────────────────────

    @Transactional
    public void leaveTeam(String userId, Long teamIdx) {
        teamRepository.findById(teamIdx)
                .filter(t -> t.getDelAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));

        TeamMember member = teamMemberRepository.findActiveByTeamIdxAndUserId(teamIdx, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));

        if ("LEADER".equals(member.getRole())) {
            throw new CustomException(ErrorCode.LEADER_CANNOT_LEAVE);
        }

        member.setExitAt(LocalDateTime.now());
        teamMemberRepository.save(member);
    }

    // ─── 채널 참여 ────────────────────────────────────────────────────────────────

    @Transactional
    public TeamSidebarResponse joinChannel(String userId, Long teamIdx, Long roomIdx) {
        Team team = teamRepository.findById(teamIdx)
                .filter(t -> t.getDelAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));

        teamMemberRepository.findActiveByTeamIdxAndUserId(teamIdx, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));

        ChatRoom chatRoom = chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getTeam() != null && r.getTeam().getTeamIdx().equals(teamIdx) && r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(chatRoom, user)) {
            return toSidebarResponse(team, userId);
        }

        RoomMember roomMember = new RoomMember();
        roomMember.setChatRoom(chatRoom);
        roomMember.setUser(user);
        roomMember.setRole("MEMBER");
        roomMember.setJoinAt(LocalDateTime.now());
        roomMemberRepository.save(roomMember);

        return toSidebarResponse(team, userId);
    }

    // ─── 내부 ─────────────────────────────────────────────────────────────────

    private TeamSidebarResponse toSidebarResponse(Team team, String userId) {
        List<TeamMember> members = teamMemberRepository.findActiveByTeamIdx(team.getTeamIdx());
        List<ChatRoom> channels = chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(team.getTeamIdx());

        String myRole = members.stream()
                .filter(m -> m.getUser().getUserId().equals(userId))
                .map(TeamMember::getRole)
                .findFirst()
                .orElse("MEMBER");

        Set<Long> joinedRoomIds = Set.of();
        if (!channels.isEmpty()) {
            List<Long> roomIds = channels.stream().map(ChatRoom::getRoomIdx).toList();
            joinedRoomIds = Set.copyOf(roomMemberRepository.findJoinedRoomIdxByUserIdAndRoomIds(userId, roomIds));
        }
        final Set<Long> joined = joinedRoomIds;

        return TeamSidebarResponse.builder()
                .teamIdx(team.getTeamIdx())
                .teamName(team.getTeamName())
                .about(team.getAbout())
                .myRole(myRole)
                .channels(channels.stream()
                        .map(ch -> new TeamChannelDto(ch.getRoomIdx(), ch.getRoomName(), joined.contains(ch.getRoomIdx())))
                        .toList())
                .members(members.stream()
                        .map(m -> new TeamMemberDto(m.getUser().getUserId(), m.getUser().getNick(), m.getRole()))
                        .toList())
                .build();
    }

    private int rolePriority(String role) {
        return switch (role) {
            case "LEADER" -> 3;
            case "MANAGER" -> 2;
            default -> 1;
        };
    }
}
