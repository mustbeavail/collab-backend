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
