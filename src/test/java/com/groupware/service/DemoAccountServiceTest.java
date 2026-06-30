package com.groupware.service;

import com.groupware.domain.User;
import com.groupware.dto.auth.AuthResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.UserRepository;
import com.groupware.websocket.WebSocketEventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemoAccountServiceTest {

    @InjectMocks private DemoAccountService service;
    @Mock private UserRepository userRepository;
    @Mock private WebSocketEventListener webSocketEventListener;
    @Mock private AuthService authService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private TestBotService testBotService;
    @Mock private DemoSessionStore demoSessionStore;
    @Mock private FileService fileService;
    @Mock private ValueOperations<String, String> valueOps;

    private User user(String id) {
        User u = new User();
        u.setUserId(id);
        u.setNick(id.substring(0, id.indexOf('@')));
        return u;
    }

    @Test
    void 미사용_계정_선택_토큰발급() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        // test2 사용중, test3 미사용
        given(webSocketEventListener.isOnline("test2@test.com")).willReturn(true);
        given(webSocketEventListener.isOnline("test3@test.com")).willReturn(false);
        given(valueOps.setIfAbsent(eq("demo-lock:test3@test.com"), any(), any(Long.class), any(TimeUnit.class)))
                .willReturn(true);
        User u3 = user("test3@test.com");
        given(userRepository.findById("test3@test.com")).willReturn(Optional.of(u3));
        AuthResponse expected = AuthResponse.builder().accessToken("AT").userId("test3@test.com").build();
        given(authService.issueDemoTokens(u3)).willReturn(expected);

        AuthResponse result = service.acquireDemoAccount();

        assertThat(result).isSameAs(expected);
        // test3에서 끝났으므로 test4는 확인하지 않음
        verify(webSocketEventListener, never()).isOnline("test4@test.com");
        // 시연 세션 시작 표시
        verify(demoSessionStore).startSession("test3@test.com");
    }

    @Test
    void 모두_사용중이면_예외() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        DemoAccountService.DEMO_ACCOUNTS.forEach(id ->
                given(webSocketEventListener.isOnline(id)).willReturn(true));

        assertThatThrownBy(() -> service.acquireDemoAccount())
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMO_ACCOUNTS_BUSY);

        verify(authService, never()).issueDemoTokens(any());
    }

    @Test
    void 시연종료_파일삭제_봇정리_락online해제_마커정리() {
        given(demoSessionStore.isSession("test3@test.com")).willReturn(true);
        given(demoSessionStore.getFiles("test3@test.com")).willReturn(java.util.Set.of(11L, 12L));

        service.cleanupDemoSession("test3@test.com");

        verify(fileService).delete("test3@test.com", 11L);
        verify(fileService).delete("test3@test.com", 12L);
        verify(testBotService).cleanupBotRelationship("test3@test.com");
        verify(authService).logoutByUserId("test3@test.com"); // 모든 종료 경로에서 서버 세션 로그아웃
        verify(redisTemplate).delete("demo-lock:test3@test.com");
        verify(webSocketEventListener).markOfflineNow("test3@test.com");
        verify(demoSessionStore).endSession("test3@test.com");
    }

    @Test
    void 시연종료_세션마커없으면_락만해제_정리안함() {
        given(demoSessionStore.isSession("test3@test.com")).willReturn(false);

        service.cleanupDemoSession("test3@test.com");

        verify(redisTemplate).delete("demo-lock:test3@test.com"); // 안전하게 예약만 해제
        verify(fileService, never()).delete(any(), any());
        verify(testBotService, never()).cleanupBotRelationship(any());
        verify(authService, never()).logoutByUserId(any());
        verify(webSocketEventListener, never()).markOfflineNow(any());
        verify(demoSessionStore, never()).endSession(any());
    }

    @Test
    void 시연종료_허용목록외_계정은_무시() {
        service.cleanupDemoSession("hacker@test.com");

        verify(redisTemplate, never()).delete(any(String.class));
        verify(testBotService, never()).cleanupBotRelationship(any());
        verify(demoSessionStore, never()).isSession(any());
    }

    @Test
    void 시연종료_null이면_무시() {
        service.cleanupDemoSession(null);

        verify(redisTemplate, never()).delete(any(String.class));
        verify(testBotService, never()).cleanupBotRelationship(any());
        verify(demoSessionStore, never()).isSession(any());
    }

    @Test
    void 예약_경합시_다음_후보로() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(webSocketEventListener.isOnline("test2@test.com")).willReturn(false);
        given(webSocketEventListener.isOnline("test3@test.com")).willReturn(false);
        // test2는 막 다른 시연이 가져감(SETNX 실패) → test3 예약 성공
        given(valueOps.setIfAbsent(eq("demo-lock:test2@test.com"), any(), any(Long.class), any(TimeUnit.class)))
                .willReturn(false);
        given(valueOps.setIfAbsent(eq("demo-lock:test3@test.com"), any(), any(Long.class), any(TimeUnit.class)))
                .willReturn(true);
        User u3 = user("test3@test.com");
        given(userRepository.findById("test3@test.com")).willReturn(Optional.of(u3));
        AuthResponse expected = AuthResponse.builder().accessToken("AT").userId("test3@test.com").build();
        given(authService.issueDemoTokens(u3)).willReturn(expected);

        AuthResponse result = service.acquireDemoAccount();

        assertThat(result.getUserId()).isEqualTo("test3@test.com");
    }
}
