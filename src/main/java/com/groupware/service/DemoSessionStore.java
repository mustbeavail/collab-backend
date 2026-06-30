package com.groupware.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 기능 시연 세션의 서버측 상태(Redis) 저장소.
 *
 * <ul>
 *   <li>demo-session:{userId} — 이 계정이 시연 진행 중임을 표시(획득 시 set, 정리 시 삭제).
 *       정리 트리거(중단/새로고침/탭종료)가 여럿이라 "정리할 세션이 있는지" 판정에 사용한다.</li>
 *   <li>demo-files:{userId} — 시연 중 업로드된 파일 idx 집합(정리 시 일괄 삭제 대상).</li>
 * </ul>
 *
 * <p>FileService·DemoAccountService·WebSocketEventListener가 공유한다. 이들 사이의 순환 의존을
 * 끊기 위해 StringRedisTemplate만 의존하는 경량 빈으로 분리했다.
 */
@Component
@RequiredArgsConstructor
public class DemoSessionStore {

    private static final String SESSION_PREFIX = "demo-session:";
    private static final String FILES_PREFIX = "demo-files:";
    /** 시연 세션/파일 추적 보존 시간(초). 시연은 이 안에 끝난다(WS online TTL과 동일). */
    private static final long SESSION_TTL_SECONDS = 3600L;

    private final StringRedisTemplate redisTemplate;

    public void startSession(String userId) {
        redisTemplate.opsForValue().set(SESSION_PREFIX + userId, "1", SESSION_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public boolean isSession(String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(SESSION_PREFIX + userId));
    }

    public void addFile(String userId, Long fileIdx) {
        String key = FILES_PREFIX + userId;
        redisTemplate.opsForSet().add(key, String.valueOf(fileIdx));
        redisTemplate.expire(key, SESSION_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public Set<Long> getFiles(String userId) {
        Set<String> raw = redisTemplate.opsForSet().members(FILES_PREFIX + userId);
        if (raw == null) return Set.of();
        return raw.stream().map(Long::valueOf).collect(Collectors.toSet());
    }

    public void endSession(String userId) {
        redisTemplate.delete(SESSION_PREFIX + userId);
        redisTemplate.delete(FILES_PREFIX + userId);
    }
}
