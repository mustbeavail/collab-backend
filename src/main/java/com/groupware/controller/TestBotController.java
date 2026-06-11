package com.groupware.controller;

import com.groupware.dto.common.ApiResponse;
import com.groupware.service.TestBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/testbot")
@RequiredArgsConstructor
public class TestBotController {

    private final TestBotService testBotService;

    /** 기능 테스트 버튼 → 테스트봇이 현재 유저에게 친구 요청을 보낸다. */
    @PostMapping("/invite")
    public ResponseEntity<ApiResponse<Void>> invite(@AuthenticationPrincipal UserDetails userDetails) {
        String message = testBotService.inviteUser(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(message, null));
    }
}
