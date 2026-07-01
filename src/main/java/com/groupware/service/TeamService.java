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
import com.groupware.websocket.WebSocketEventListener;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketEventListener wsEventListener;

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

        // 실시간 팀초대 알림(qa 항목12) — 대상에게 WS 푸시(새로고침 없이 알림벨 반영)
        messagingTemplate.convertAndSendToUser(
                targetUserId,
                "/queue/notifications",
                NotificationPayload.builder()
                        .type("TEAM_INVITE")
                        .tmIdx(invitation.getTmIdx())
                        .teamIdx(team.getTeamIdx())
                        .teamName(team.getTeamName())
                        .build()
        );
    }

    // 팀 정보 조회(qa 항목20) — 멤버이거나 대기중 초대를 받은 사용자만(초대 알림에서 팀 정보 보기)
    @Transactional(readOnly = true)
    public TeamSidebarResponse getTeamInfo(String userId, Long teamIdx) {
        Team team = teamRepository.findById(teamIdx)
                .filter(t -> t.getDelAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        // findCurrent: exitAt null인 멤버십(PENDING 초대 포함)
        if (teamMemberRepository.findCurrentByTeamIdxAndUserId(teamIdx, userId).isEmpty()) {
            throw new CustomException(ErrorCode.NOT_TEAM_MEMBER);
        }
        return toSidebarResponse(team, userId);
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
        // 초대 수락 전에 팀이 삭제되었으면 입장 불가(초대→삭제→수락 시나리오)
        if (invitation.getTeam().getDelAt() != null) {
            // 죽은 초대는 정리(다시 뜨지 않도록 exitAt 마킹)
            invitation.setExitAt(LocalDateTime.now());
            teamMemberRepository.save(invitation);
            throw new CustomException(ErrorCode.TEAM_ALREADY_DELETED);
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

        // 추방된 멤버도 팀 채팅방 전부에서 나가게(버그 항목22 일관성)
        exitAllTeamRooms(teamIdx, target.getUser());
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

        // 팀에서 나가면 참여 중이던 팀 채팅방 전부에서 일괄 탈퇴(버그 항목22)
        exitAllTeamRooms(teamIdx, member.getUser());
    }

    /**
     * 회원 탈퇴 시 그가 LEADER인 팀들의 리더권을 자동 위임(추가2).
     * 우선순위: MANAGER → MEMBER, 동순위면 먼저 들어온 사람(joinAt 빠른 순). 남은 멤버 없으면 팀 소프트 삭제.
     */
    @Transactional
    public void reassignLeadershipForWithdrawnUser(String userId) {
        List<TeamMember> leaderships = teamMemberRepository.findActiveLeaderMembershipsByUserId(userId);
        for (TeamMember lead : leaderships) {
            Long teamIdx = lead.getTeam().getTeamIdx();
            List<TeamMember> candidates = teamMemberRepository.findActiveByTeamIdx(teamIdx).stream()
                    .filter(m -> !m.getUser().getUserId().equals(userId))
                    .sorted(java.util.Comparator
                            .comparingInt((TeamMember m) -> rolePriority(m.getRole())).reversed()
                            .thenComparing(TeamMember::getJoinAt,
                                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                    .toList();
            if (candidates.isEmpty()) {
                lead.getTeam().setDelAt(LocalDateTime.now());
                teamRepository.save(lead.getTeam());
            } else {
                TeamMember successor = candidates.get(0);
                successor.setRole("LEADER");
                teamMemberRepository.save(successor);
            }
            lead.setExitAt(LocalDateTime.now());
            teamMemberRepository.save(lead);
            exitAllTeamRooms(teamIdx, lead.getUser());
        }
    }

    /** 사용자를 해당 팀의 모든 채팅방 멤버십에서 나가게 한다(팀 탈퇴/추방 시 사용). */
    private void exitAllTeamRooms(Long teamIdx, User user) {
        List<ChatRoom> rooms = chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(teamIdx);
        for (ChatRoom room : rooms) {
            roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, user)
                    .ifPresent(rm -> {
                        rm.setExitAt(LocalDateTime.now());
                        roomMemberRepository.save(rm);
                    });
        }
    }

    // ─── 채널 생성 ────────────────────────────────────────────────────────────────

    // 팀 채팅방(채널) 추가(qa 항목21). 생성자만 입장시킴(팀 가입 유저 자동입장 차단).
    @Transactional
    public TeamSidebarResponse createChannel(String userId, Long teamIdx, CreateChannelRequest request) {
        Team team = teamRepository.findById(teamIdx)
                .filter(t -> t.getDelAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        teamMemberRepository.findActiveByTeamIdxAndUserId(teamIdx, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));

        ChatRoom room = new ChatRoom();
        room.setTeam(team);
        room.setRoomName(request.getRoomName());
        room.setCreatedAt(LocalDateTime.now());
        room = chatRoomRepository.save(room);

        // 생성자만 RoomMember로 등록(나머지 팀원은 '참여' 버튼으로 직접 입장)
        RoomMember owner = new RoomMember();
        owner.setChatRoom(room);
        owner.setUser(user);
        owner.setRole("OWNER");
        owner.setJoinAt(LocalDateTime.now());
        roomMemberRepository.save(owner);

        return toSidebarResponse(team, userId);
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
        java.util.Map<Long, Integer> memberCounts = new java.util.HashMap<>();
        Set<Long> ownedRoomIds = Set.of();
        if (!channels.isEmpty()) {
            List<Long> roomIds = channels.stream().map(ChatRoom::getRoomIdx).toList();
            joinedRoomIds = Set.copyOf(roomMemberRepository.findJoinedRoomIdxByUserIdAndRoomIds(userId, roomIds));
            // 활성 멤버 수 배치 조회(I-14)
            for (Object[] row : roomMemberRepository.countActiveByRoomIds(roomIds)) {
                memberCounts.put((Long) row[0], ((Long) row[1]).intValue());
            }
            // 내가 OWNER인 채널(I-13: 이름변경 권한)
            ownedRoomIds = Set.copyOf(roomMemberRepository.findOwnedRoomIdxByUserIdAndRoomIds(userId, roomIds));
        }
        final Set<Long> joined = joinedRoomIds;
        final java.util.Map<Long, Integer> counts = memberCounts;
        final Set<Long> owned = ownedRoomIds;

        return TeamSidebarResponse.builder()
                .teamIdx(team.getTeamIdx())
                .teamName(team.getTeamName())
                .about(team.getAbout())
                .myRole(myRole)
                .channels(channels.stream()
                        .map(ch -> new TeamChannelDto(ch.getRoomIdx(), ch.getRoomName(),
                                joined.contains(ch.getRoomIdx()),
                                counts.getOrDefault(ch.getRoomIdx(), 0),
                                owned.contains(ch.getRoomIdx())))
                        .toList())
                .members(members.stream()
                        // 리더 → 매니저 → 멤버 순 정렬(qa 항목18)
                        .sorted(java.util.Comparator.comparingInt((TeamMember m) -> rolePriority(m.getRole())).reversed())
                        .map(m -> new TeamMemberDto(
                                m.getUser().getUserId(),
                                m.getUser().getNick(),
                                m.getRole(),
                                m.getUser().getAvatarUrl(),
                                wsEventListener.isOnline(m.getUser().getUserId())))
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
