package com.groupware.service;

import com.groupware.dto.auth.EmailSendRequest;
import com.groupware.dto.auth.EmailVerifyRequest;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String CODE_KEY_PREFIX = "email:verify:";
    private static final String ATTEMPTS_KEY_PREFIX = "email:verify:attempts:";
    private static final String SENT_KEY_PREFIX = "email:verify:sent:";
    private static final String VERIFIED_KEY_PREFIX = "email:verified:";
    private static final long CODE_EXPIRY_MINUTES = 5;
    private static final long SEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;
    private static final long VERIFIED_EXPIRY_MINUTES = 30;

    public void sendCode(EmailSendRequest request) {
        String email = request.getEmail();
        String sentKey = SENT_KEY_PREFIX + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(sentKey))) {
            throw new CustomException(ErrorCode.EMAIL_SEND_COOLDOWN);
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, code, CODE_EXPIRY_MINUTES, TimeUnit.MINUTES);
        redisTemplate.delete(ATTEMPTS_KEY_PREFIX + email);
        redisTemplate.opsForValue().set(sentKey, "1", SEND_COOLDOWN_SECONDS, TimeUnit.SECONDS);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("[Collab] 이메일 인증코드");
            message.setText("인증코드: " + code + "\n\n5분 내에 입력해 주세요.");
            mailSender.send(message);
        } catch (MailException e) {
            redisTemplate.delete(CODE_KEY_PREFIX + email);
            redisTemplate.delete(sentKey);
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public void verifyCode(EmailVerifyRequest request) {
        String email = request.getEmail();
        String codeKey = CODE_KEY_PREFIX + email;
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;

        String stored = redisTemplate.opsForValue().get(codeKey);
        if (stored == null) {
            throw new CustomException(ErrorCode.EMAIL_CODE_EXPIRED);
        }

        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr == null ? 0 : Integer.parseInt(attemptsStr);
        if (attempts >= MAX_ATTEMPTS) {
            redisTemplate.delete(codeKey);
            throw new CustomException(ErrorCode.EMAIL_CODE_EXPIRED);
        }

        if (!stored.equals(request.getCode())) {
            redisTemplate.opsForValue().increment(attemptsKey);
            redisTemplate.expire(attemptsKey, CODE_EXPIRY_MINUTES, TimeUnit.MINUTES);
            throw new CustomException(ErrorCode.EMAIL_CODE_INVALID);
        }

        redisTemplate.delete(codeKey);
        redisTemplate.delete(attemptsKey);
        redisTemplate.opsForValue().set(VERIFIED_KEY_PREFIX + email, "1", VERIFIED_EXPIRY_MINUTES, TimeUnit.MINUTES);
    }

    public void consumeVerified(String email) {
        String key = VERIFIED_KEY_PREFIX + email;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        redisTemplate.delete(key);
    }
}
