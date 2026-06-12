package com.groupware.controller;

import com.groupware.dto.common.ApiResponse;
import com.groupware.dto.friend.FriendRequestDto;
import com.groupware.dto.friend.FriendResponse;
import com.groupware.service.FriendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FriendResponse>>> getFriends(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(friendService.getFriends(userDetails.getUsername())));
    }

    @GetMapping("/online-status")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getOnlineStatuses(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(friendService.getOnlineStatuses(userDetails.getUsername())));
    }

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<List<FriendResponse>>> getPendingRequests(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(friendService.getPendingRequests(userDetails.getUsername())));
    }

    // 내가 보낸 대기중 요청 목록(qa 항목6)
    @GetMapping("/requests/sent")
    public ResponseEntity<ApiResponse<List<FriendResponse>>> getSentRequests(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(friendService.getSentRequests(userDetails.getUsername())));
    }

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<Void>> sendRequest(
            @RequestBody @Valid FriendRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        friendService.sendRequest(dto, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("친구 요청을 보냈습니다.", null));
    }

    @PatchMapping("/{friendIdx}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptRequest(
            @PathVariable Long friendIdx,
            @AuthenticationPrincipal UserDetails userDetails) {
        friendService.acceptRequest(friendIdx, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("친구 요청을 수락했습니다.", null));
    }

    @PatchMapping("/{friendIdx}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectRequest(
            @PathVariable Long friendIdx,
            @AuthenticationPrincipal UserDetails userDetails) {
        friendService.rejectRequest(friendIdx, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("친구 요청을 거절했습니다.", null));
    }

    @DeleteMapping("/{friendIdx}")
    public ResponseEntity<ApiResponse<Void>> deleteFriend(
            @PathVariable Long friendIdx,
            @AuthenticationPrincipal UserDetails userDetails) {
        friendService.deleteFriend(friendIdx, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("친구를 삭제했습니다.", null));
    }
}
