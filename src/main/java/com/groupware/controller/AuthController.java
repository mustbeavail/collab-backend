package com.groupware.controller;

import com.groupware.dto.auth.AuthResponse;
import com.groupware.dto.auth.LoginRequest;
import com.groupware.dto.auth.SignupRequest;
import com.groupware.dto.common.ApiResponse;
import com.groupware.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @RequestBody @Valid SignupRequest request,
            HttpServletResponse response) {
        AuthResponse auth = authService.signup(request);
        setRefreshTokenCookie(response, auth.getRefreshToken());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("회원가입이 완료되었습니다.", auth.withoutRefreshToken()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletResponse response) {
        AuthResponse auth = authService.login(request);
        setRefreshTokenCookie(response, auth.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(auth.withoutRefreshToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = extractRefreshTokenCookie(request);
        AuthResponse auth = authService.refresh(refreshToken);
        setRefreshTokenCookie(response, auth.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(auth.withoutRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticationPrincipal UserDetails userDetails) {
        String refreshToken = extractRefreshTokenCookie(request);
        String accessToken = extractBearerToken(request);
        authService.logout(refreshToken, accessToken);
        clearRefreshTokenCookie(response);
        return ResponseEntity.ok(ApiResponse.ok("로그아웃되었습니다.", null));
    }

    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Void>> checkEmail(@RequestParam String email) {
        authService.checkEmail(email);
        return ResponseEntity.ok(ApiResponse.ok("사용 가능한 이메일입니다.", null));
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<ApiResponse<Void>> checkNickname(
            @RequestParam String nickname,
            @AuthenticationPrincipal UserDetails userDetails) {
        String currentUserId = userDetails != null ? userDetails.getUsername() : null;
        authService.checkNickname(nickname, currentUserId);
        return ResponseEntity.ok(ApiResponse.ok("사용 가능한 닉네임입니다.", null));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge((int) (refreshTokenExpiry / 1000));
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);

        // logout 경로도 쿠키 접근 허용
        Cookie logoutCookie = new Cookie("refreshToken", refreshToken);
        logoutCookie.setHttpOnly(true);
        logoutCookie.setPath("/api/auth/logout");
        logoutCookie.setMaxAge((int) (refreshTokenExpiry / 1000));
        logoutCookie.setAttribute("SameSite", "Lax");
        response.addCookie(logoutCookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie c1 = new Cookie("refreshToken", "");
        c1.setHttpOnly(true);
        c1.setPath("/api/auth/refresh");
        c1.setMaxAge(0);
        response.addCookie(c1);

        Cookie c2 = new Cookie("refreshToken", "");
        c2.setHttpOnly(true);
        c2.setPath("/api/auth/logout");
        c2.setMaxAge(0);
        response.addCookie(c2);
    }

    private String extractRefreshTokenCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            throw new com.groupware.exception.CustomException(
                    com.groupware.exception.ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> "refreshToken".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new com.groupware.exception.CustomException(
                        com.groupware.exception.ErrorCode.REFRESH_TOKEN_NOT_FOUND));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
