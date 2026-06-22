package com.groupware.controller;

import com.groupware.dto.common.ApiResponse;
import com.groupware.dto.user.ChangePasswordRequest;
import com.groupware.dto.user.UpdateProfileRequest;
import com.groupware.dto.user.UserProfileResponse;
import com.groupware.dto.user.UserSearchResponse;
import com.groupware.service.FriendService;
import com.groupware.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final FriendService friendService;
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyProfile(userDetails.getUsername())));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateMyProfile(userDetails.getUsername(), request)));
    }

    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.ok("비밀번호가 변경되었습니다.", null));
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAvatar(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        String avatarUrl = userService.uploadAvatar(userDetails.getUsername(), file);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("avatarUrl", avatarUrl)));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<ApiResponse<Void>> deleteAvatar(
            @AuthenticationPrincipal UserDetails userDetails) {
        userService.deleteAvatar(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("프로필 사진이 제거되었습니다.", null));
    }

    // /{userId} 보다 먼저 매칭되도록(리터럴 경로 우선). 본인 제외 닉네임 중복 검사.
    @GetMapping("/check-nickname")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkNickname(
            @RequestParam String nickname,
            @AuthenticationPrincipal UserDetails userDetails) {
        boolean available = userService.isNicknameAvailable(userDetails.getUsername(), nickname);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("available", available)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal UserDetails userDetails) {
        userService.withdraw(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("회원 탈퇴가 완료되었습니다.", null));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @PathVariable String userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String currentUserId = userDetails != null ? userDetails.getUsername() : null;
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserPublicProfile(currentUserId, userId)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<UserSearchResponse>>> search(
            @RequestParam String q,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(friendService.searchUsers(q, userDetails.getUsername())));
    }
}
