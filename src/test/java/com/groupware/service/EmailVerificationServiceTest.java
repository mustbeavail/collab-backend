package com.groupware.service;

import com.groupware.dto.auth.EmailSendRequest;
import com.groupware.dto.auth.EmailVerifyRequest;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Mock private JavaMailSender mailSender;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        // requireVerified 테스트는 opsForValue를 쓰지 않으므로 lenient로 둔다
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ─── sendCode ─────────────────────────────────────────────────────────

    @Test
    void 코드_발송_성공() {
        EmailSendRequest request = sendRequest("test@example.com");
        willDoNothing().given(mailSender).send(any(SimpleMailMessage.class));

        emailVerificationService.sendCode(request);

        verify(valueOps).set(eq("email:verify:test@example.com"), anyString(), eq(5L), any());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void 코드_발송_실패시_Redis_롤백_후_예외() {
        EmailSendRequest request = sendRequest("fail@example.com");
        willThrow(new MailSendException("connection refused")).given(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> emailVerificationService.sendCode(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_SEND_FAILED));

        verify(redisTemplate).delete("email:verify:fail@example.com");
    }

    // ─── verifyCode ───────────────────────────────────────────────────────

    @Test
    void 코드_인증_성공() {
        EmailVerifyRequest request = verifyRequest("test@example.com", "123456");
        given(valueOps.get("email:verify:test@example.com")).willReturn("123456");

        emailVerificationService.verifyCode(request);

        verify(redisTemplate).delete("email:verify:test@example.com");
    }

    @Test
    void 코드_인증_만료_예외() {
        EmailVerifyRequest request = verifyRequest("test@example.com", "123456");
        given(valueOps.get("email:verify:test@example.com")).willReturn(null);

        assertThatThrownBy(() -> emailVerificationService.verifyCode(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_CODE_EXPIRED));

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void 코드_인증_불일치_예외() {
        EmailVerifyRequest request = verifyRequest("test@example.com", "000000");
        given(valueOps.get("email:verify:test@example.com")).willReturn("123456");

        assertThatThrownBy(() -> emailVerificationService.verifyCode(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_CODE_INVALID));

        verify(redisTemplate, never()).delete(anyString());
    }

    // ─── requireVerified / consumeVerified ────────────────────────────────

    @Test
    void requireVerified_인증됨_통과_키삭제안함() {
        given(redisTemplate.hasKey("email:verified:test@example.com")).willReturn(true);

        emailVerificationService.requireVerified("test@example.com"); // 예외 없이 통과

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void requireVerified_인증안됨_예외() {
        given(redisTemplate.hasKey("email:verified:none@example.com")).willReturn(false);

        assertThatThrownBy(() -> emailVerificationService.requireVerified("none@example.com"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void consumeVerified_인증됨_키삭제() {
        given(redisTemplate.hasKey("email:verified:test@example.com")).willReturn(true);

        emailVerificationService.consumeVerified("test@example.com");

        verify(redisTemplate).delete("email:verified:test@example.com");
    }

    @Test
    void consumeVerified_인증안됨_예외() {
        given(redisTemplate.hasKey("email:verified:none@example.com")).willReturn(false);

        assertThatThrownBy(() -> emailVerificationService.consumeVerified("none@example.com"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private EmailSendRequest sendRequest(String email) {
        EmailSendRequest req = new EmailSendRequest();
        ReflectionTestUtils.setField(req, "email", email);
        return req;
    }

    private EmailVerifyRequest verifyRequest(String email, String code) {
        EmailVerifyRequest req = new EmailVerifyRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "code", code);
        return req;
    }
}
