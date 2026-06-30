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
    private final TestBotService testBotService;
    private final DemoSessionStore demoSessionStore;
    private final FileService fileService;

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
            // 시연 세션 시작 표시(이후 업로드 파일 추적·종료 정리 판정에 사용)
            demoSessionStore.startSession(userId);
            return authService.issueDemoTokens(user);
        }
        throw new CustomException(ErrorCode.DEMO_ACCOUNTS_BUSY);
    }

    /**
     * 시연 종료 시 한 곳에서 모든 정리를 수행한다(서버 일원화).
     * 트리거: ① 중단/완료(POST /api/demo/release) ② 새로고침·탭종료(release-beacon) ③ 크래시·강제종료(WS 끊김 이벤트).
     * 어느 경로로 들어와도 동일하게 동작하며, 세션 마커로 중복 호출을 무력화한다(idempotent).
     *
     * <p>정리 내용: 시연 중 업로드 파일 삭제 → 봇 친구/DM 정리 → demo-lock·online 해제 → 세션/파일 마커 삭제.
     * 허용목록(시연계정)이며 세션 마커가 있을 때만 동작 → 실유저·수동 로그인 데이터는 건드리지 않는다.
     */
    public void cleanupDemoSession(String userId) {
        if (userId == null || !DEMO_ACCOUNTS.contains(userId)) return;
        if (!demoSessionStore.isSession(userId)) {
            // 이미 정리됐거나 시연 세션이 아님 → 혹시 남은 예약만 안전하게 해제하고 종료
            redisTemplate.delete(DEMO_LOCK_PREFIX + userId);
            return;
        }

        // 1) 시연 중 업로드한 파일 삭제(개별 실패는 무시)
        for (Long fileIdx : demoSessionStore.getFiles(userId)) {
            try { fileService.delete(userId, fileIdx); } catch (Exception ignored) { /* 이미 삭제 등 */ }
        }
        // 2) 봇 친구·DM 정리(양방향·요청/수락, 봇 DM 나가기)
        testBotService.cleanupBotRelationship(userId);
        // 3) 서버 세션 로그아웃 — 중단 버튼 외(새로고침·탭종료·크래시) 경로도 토큰 없이 세션을 무효화한다.
        authService.logoutByUserId(userId);
        // 4) 예약·온라인 즉시 해제(3분 대기 없이 바로 재시연 가능)
        redisTemplate.delete(DEMO_LOCK_PREFIX + userId);
        webSocketEventListener.markOfflineNow(userId);
        // 5) 세션·파일 마커 정리(이후 중복 트리거는 no-op)
        demoSessionStore.endSession(userId);
    }
}
