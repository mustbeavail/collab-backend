package com.groupware.controller;

import com.groupware.dto.auth.AuthResponse;
import com.groupware.dto.common.ApiResponse;
import com.groupware.security.JwtUtil;
import com.groupware.service.DemoAccountService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final JwtUtil jwtUtil;

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

    /**
     * 시연 종료(완료/중단 버튼) 시 호출. 시연 부산물(파일·봇 친구/DM)을 정리하고 예약·online을 즉시 해제한다.
     * 로그아웃 직전(토큰 유효) 호출해야 한다.
     */
    @PostMapping("/release")
    public ResponseEntity<ApiResponse<Void>> release(@AuthenticationPrincipal UserDetails userDetails) {
        demoAccountService.cleanupDemoSession(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * 새로고침·탭종료 시 navigator.sendBeacon으로 호출되는 정리 엔드포인트.
     * sendBeacon은 Authorization 헤더를 못 실으므로 액세스 토큰을 본문(text/plain)으로 받아 검증한다.
     * CORS 프리플라이트를 피하려고 text/plain을 쓴다(단순 요청). permitAll(SecurityConfig)이며 토큰 유효성으로 보호.
     */
    @PostMapping(value = "/release-beacon", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Void> releaseBeacon(@RequestBody(required = false) String token) {
        if (token != null && !token.isBlank() && jwtUtil.validate(token)) {
            demoAccountService.cleanupDemoSession(jwtUtil.getUserId(token));
        }
        return ResponseEntity.ok().build();
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
