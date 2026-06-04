package com.groupware.controller;

import com.groupware.dto.auth.EmailSendRequest;
import com.groupware.dto.auth.EmailVerifyRequest;
import com.groupware.dto.common.ApiResponse;
import com.groupware.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping("/send-code")
    public ResponseEntity<ApiResponse<Void>> sendCode(@RequestBody @Valid EmailSendRequest request) {
        emailVerificationService.sendCode(request);
        return ResponseEntity.ok(ApiResponse.ok("인증코드가 발송되었습니다.", null));
    }

    @PostMapping("/verify-code")
    public ResponseEntity<ApiResponse<Void>> verifyCode(@RequestBody @Valid EmailVerifyRequest request) {
        emailVerificationService.verifyCode(request);
        return ResponseEntity.ok(ApiResponse.ok("이메일 인증이 완료되었습니다.", null));
    }
}
