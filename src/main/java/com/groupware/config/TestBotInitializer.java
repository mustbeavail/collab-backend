package com.groupware.config;

import com.groupware.service.TestBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/**
 * 시동 시 테스트봇 계정을 멱등 생성한다.
 */
@Component
@RequiredArgsConstructor
public class TestBotInitializer implements ApplicationRunner {

    private final TestBotService testBotService;

    @Override
    public void run(ApplicationArguments args) {
        testBotService.ensureBotAccount();
    }
}
