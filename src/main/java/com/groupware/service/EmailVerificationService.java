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

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "email:verify:";
    private static final long CODE_EXPIRY_MINUTES = 5;

    public void sendCode(EmailSendRequest request) {
        String code = String.format("%06d", new Random().nextInt(1_000_000));

        redisTemplate.opsForValue().set(KEY_PREFIX + request.getEmail(), code, CODE_EXPIRY_MINUTES, TimeUnit.MINUTES);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(request.getEmail());
            message.setSubject("[Collab] 이메일 인증코드");
            message.setText("인증코드: " + code + "\n\n5분 내에 입력해 주세요.");
            mailSender.send(message);
        } catch (MailException e) {
            redisTemplate.delete(KEY_PREFIX + request.getEmail());
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public void verifyCode(EmailVerifyRequest request) {
        String key = KEY_PREFIX + request.getEmail();
        String stored = redisTemplate.opsForValue().get(key);

        if (stored == null) {
            throw new CustomException(ErrorCode.EMAIL_CODE_EXPIRED);
        }

        if (!stored.equals(request.getCode())) {
            throw new CustomException(ErrorCode.EMAIL_CODE_INVALID);
        }

        redisTemplate.delete(key);
    }
}
