package com.groupware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // [I] 녹음 만료 정리 배치(RecordingCleanupService) 활성화
public class GroupwareApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroupwareApplication.class, args);
    }
}
