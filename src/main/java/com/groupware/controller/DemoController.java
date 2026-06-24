package com.groupware.controller;

import com.groupware.dto.auth.AuthResponse;
import com.groupware.dto.common.ApiResponse;
import com.groupware.service.DemoAccountService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기능 시연(가이드 투어) 진입용. 로그인 없이 미사용 테스트계정을 받아 자동 로그인한다.
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoAccountService demoAccountService;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @PostMapping("/account")
    public ResponseEntity<ApiResponse<AuthResponse>> acquireAccount(HttpServletResponse response) {
        AuthResponse auth = demoAccountService.acquireDemoAccount();
        setRefreshTokenCookie(response, auth.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(auth.withoutRefreshToken()));
    }

    // AuthController와 동일한 refresh 쿠키 정책
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        int maxAge = (int) (refreshTokenExpiry / 1000);
        response.addCookie(buildRefreshCookie("/api/auth/refresh", refreshToken, maxAge));
        response.addCookie(buildRefreshCookie("/api/auth/logout", refreshToken, maxAge));
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
}
