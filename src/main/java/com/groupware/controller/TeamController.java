package com.groupware.controller;

import com.groupware.dto.common.ApiResponse;
import com.groupware.dto.team.*;
import com.groupware.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<TeamSidebarResponse>>> getMyTeams(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.getMyTeams(userDetails.getUsername())));
    }

    // 팀 정보 조회(qa 항목20 — 초대 알림에서 팀 정보 보기). 멤버/초대받은 사용자만.
    @GetMapping("/{teamIdx}/info")
    public ResponseEntity<ApiResponse<TeamSidebarResponse>> getTeamInfo(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long teamIdx) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.getTeamInfo(userDetails.getUsername(), teamIdx)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeamSidebarResponse>> createTeam(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateTeamRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.createTeam(userDetails.getUsername(), request)));
    }

    @PutMapping("/{teamIdx}")
    public ResponseEntity<ApiResponse<TeamSidebarResponse>> updateTeam(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long teamIdx,
            @Valid @RequestBody UpdateTeamRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.updateTeam(userDetails.getUsername(), teamIdx, request)));
    }

    @DeleteMapping("/{teamIdx}")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long teamIdx) {
        teamService.deleteTeam(userDetails.getUsername(), teamIdx);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ─── 초대 ─────────────────────────────────────────────────────────────────

    @PostMapping("/{teamIdx}/invitations")
    public ResponseEntity<ApiResponse<Void>> inviteMember(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long teamIdx,
            @Valid @RequestBody InviteTeamMemberRequest request) {
        teamService.inviteMember(userDetails.getUsername(), teamIdx, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/invitations")
    public ResponseEntity<ApiResponse<List<TeamInvitationResponse>>> getMyInvitations(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.getMyInvitations(userDetails.getUsername())));
    }

    @PatchMapping("/invitations/{tmIdx}/accept")
    public ResponseEntity<ApiResponse<TeamSidebarResponse>> acceptInvitation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long tmIdx) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.acceptInvitation(userDetails.getUsername(), tmIdx)));
    }

    @PatchMapping("/invitations/{tmIdx}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectInvitation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long tmIdx) {
        teamService.rejectInvitation(userDetails.getUsername(), tmIdx);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ─── 멤버 관리 ────────────────────────────────────────────────────────────

    @DeleteMapping("/{teamIdx}/members/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> kickMember(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long teamIdx,
            @PathVariable String targetUserId) {
        teamService.kickMember(userDetails.getUsername(), teamIdx, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{teamIdx}/members/{targetUserId}/role")
    public ResponseEntity<ApiResponse<TeamSidebarResponse>> changeMemberRole(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long teamIdx,
            @PathVariable String targetUserId,
            @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                teamService.changeMemberRole(userDetails.getUsername(), teamIdx, targetUserId, request)));
    }

    // ─── 팀 나가기 ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{teamIdx}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveTeam(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long teamIdx) {
        teamService.leaveTeam(userDetails.getUsername(), teamIdx);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ─── 채널 생성/참여 ────────────────────────────────────────────────────────────────

    // 팀 채널 추가(qa 항목21)
    @PostMapping("/{teamIdx}/channels")
    public ResponseEntity<ApiResponse<TeamSidebarResponse>> createChannel(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long teamIdx,
            @Valid @RequestBody CreateChannelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                teamService.createChannel(userDetails.getUsername(), teamIdx, request)));
    }

    @PostMapping("/{teamIdx}/channels/{roomIdx}/join")
    public ResponseEntity<ApiResponse<TeamSidebarResponse>> joinChannel(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long teamIdx,
            @PathVariable Long roomIdx) {
        return ResponseEntity.ok(ApiResponse.ok(
                teamService.joinChannel(userDetails.getUsername(), teamIdx, roomIdx)));
    }
}
