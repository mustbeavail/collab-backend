package com.groupware.service;

import com.groupware.domain.User;
import com.groupware.dto.auth.AuthResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.UserRepository;
import com.groupware.websocket.WebSocketEventListener;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 기능 시연(가이드 투어)용 미사용 테스트계정 획득.
 *
 * <p>로그인 화면 '기능 시연' 버튼 → 이 서비스가 test2~5 중 현재 사용중이 아닌 계정을 골라
 * 비밀번호 없이 토큰을 발급한다(허용목록 안의 계정에만). 모두 사용중이면 예외.
 *
 * <p>'사용중' 판정: ① WS 온라인 상태(online:{id}, 시연 진행중인 계정) ② demo-lock(획득 직후
 * WS 접속 전 짧은 경합 구간 예약, SETNX TTL). 둘 중 하나라도 걸리면 건너뛴다.
 */
@Service
@RequiredArgsConstructor
public class DemoAccountService {

    /** 시연에 사용할 테스트 계정 허용목록(이 계정에만 비번 없이 토큰 발급). */
    static final List<String> DEMO_ACCOUNTS = List.of(
            "test2@test.com", "test3@test.com", "test4@test.com", "test5@test.com");

    private static final String DEMO_LOCK_PREFIX = "demo-lock:";
    /** 획득 후 WS 접속 전 경합을 막는 예약 TTL(초). 접속되면 online 상태가 이어받음. */
    private static final long DEMO_LOCK_TTL_SECONDS = 120L;

    private final UserRepository userRepository;
    private final WebSocketEventListener webSocketEventListener;
    private final AuthService authService;
    private final StringRedisTemplate redisTemplate;

    public AuthResponse acquireDemoAccount() {
        for (String userId : DEMO_ACCOUNTS) {
            // 이미 시연중(WS 온라인)인 계정은 건너뜀
            if (webSocketEventListener.isOnline(userId)) continue;

            // 접속 전 경합 방지: 원자적 예약(SETNX). 실패하면 다른 시연이 막 가져간 것.
            Boolean reserved = redisTemplate.opsForValue()
                    .setIfAbsent(DEMO_LOCK_PREFIX + userId, "1", DEMO_LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(reserved)) continue;

            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getWithdrawalAt() != null) {
                redisTemplate.delete(DEMO_LOCK_PREFIX + userId); // 예약 해제 후 다음 후보
                continue;
            }
            return authService.issueDemoTokens(user);
        }
        throw new CustomException(ErrorCode.DEMO_ACCOUNTS_BUSY);
    }
}
