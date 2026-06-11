package com.groupware.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 테스트봇 응답 생성 등 비동기 작업을 위한 @Async 활성화.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
