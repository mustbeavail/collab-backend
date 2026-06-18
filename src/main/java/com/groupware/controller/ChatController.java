package com.groupware.controller;

import com.groupware.dto.chat.ChatRoomDetailResponse;
import com.groupware.dto.chat.ChatRoomResponse;
import com.groupware.dto.chat.DmRoomResponse;
import com.groupware.dto.chat.InviteRequest;
import com.groupware.dto.chat.MessagePageResponse;
import com.groupware.dto.chat.RoomMemberResponse;
import com.groupware.dto.chat.UpdateRoomInfoRequest;
import com.groupware.dto.common.ApiResponse;
import com.groupware.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // 내 DM/그룹 채팅방 목록(qa 항목15)
    @GetMapping("/rooms/my")
    public ResponseEntity<ApiResponse<List<DmRoomResponse>>> getMyDmRooms(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(chatService.getMyDmRooms(userDetails.getUsername())));
    }

    @GetMapping("/rooms/{roomIdx}")
    public ResponseEntity<ApiResponse<ChatRoomDetailResponse>> getRoomInfo(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatService.getRoomInfo(userDetails.getUsername(), roomIdx)));
    }

    @PutMapping("/rooms/{roomIdx}")
    public ResponseEntity<ApiResponse<ChatRoomDetailResponse>> updateRoomInfo(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx,
            @Valid @RequestBody UpdateRoomInfoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatService.updateRoomInfo(userDetails.getUsername(), roomIdx, request)));
    }

    @GetMapping("/rooms/{roomIdx}/messages")
    public ResponseEntity<ApiResponse<MessagePageResponse>> getMessages(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatService.getMessages(userDetails.getUsername(), roomIdx, before, size)));
    }

    @GetMapping("/rooms/{roomIdx}/members")
    public ResponseEntity<ApiResponse<List<RoomMemberResponse>>> getMembers(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatService.getRoomMembers(userDetails.getUsername(), roomIdx)));
    }

    @PostMapping("/rooms/{roomIdx}/members/invite")
    public ResponseEntity<ApiResponse<Void>> invite(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx,
            @Valid @RequestBody InviteRequest request) {
        chatService.inviteToRoom(userDetails.getUsername(), roomIdx, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/rooms/{roomIdx}/leave")
    public ResponseEntity<ApiResponse<Void>> leave(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx) {
        chatService.leaveRoom(userDetails.getUsername(), roomIdx);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // 팀 채팅방 삭제(팀 LEADER만)
    @DeleteMapping("/rooms/{roomIdx}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx) {
        chatService.deleteRoom(userDetails.getUsername(), roomIdx);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/rooms/{roomIdx}/members/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> kick(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx,
            @PathVariable String targetUserId) {
        chatService.kickMember(userDetails.getUsername(), roomIdx, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/rooms/dm/{targetUserId}")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> getOrCreateDmRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatService.getOrCreateDmRoom(userDetails.getUsername(), targetUserId)));
    }
}
