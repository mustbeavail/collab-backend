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

    // 로컬/개발(동일 도메인)은 Lax/non-secure, 운영(크로스 도메인 HTTPS)은 None/Secure
    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

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
        int maxAge = (int) (refreshTokenExpiry / 1000);
        // refresh / logout 두 경로에서 쿠키 접근 허용
        response.addCookie(buildRefreshCookie("/api/auth/refresh", refreshToken, maxAge));
        response.addCookie(buildRefreshCookie("/api/auth/logout", refreshToken, maxAge));
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        // 삭제 쿠키도 동일 속성(SameSite/Secure)으로 내려야 브라우저가 제대로 만료시킨다
        response.addCookie(buildRefreshCookie("/api/auth/refresh", "", 0));
        response.addCookie(buildRefreshCookie("/api/auth/logout", "", 0));
    }

    private Cookie buildRefreshCookie(String path, String value, int maxAge) {
        Cookie cookie = new Cookie("refreshToken", value);
        cookie.setHttpOnly(true);
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);
        cookie.setSecure(cookieSecure);
        cookie.setAttribute("SameSite", cookieSameSite);
        return cookie;
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
