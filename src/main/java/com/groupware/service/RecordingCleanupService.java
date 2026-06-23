package com.groupware.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * [I] 음성/화상 채팅 녹음 파일 30일 보관 후 자동 정리(항목20·21).
 * 매일 새벽 4시에 만료된 녹음(expires_at < now)을 물리 파일 + DB 레코드까지 삭제한다.
 * 조회(getRecordings)는 만료 전(expires_at > now)만 반환하므로, 배치 전이라도 만료건은 목록에 노출되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingCleanupService {

    private final FileService fileService;

    @Scheduled(cron = "0 0 4 * * *") // 매일 04:00
    public void cleanupExpiredRecordings() {
        int deleted = fileService.deleteExpiredRecordings();
        if (deleted > 0) {
            log.info("[RecordingCleanup] 만료된 녹음 {}건 삭제", deleted);
        }
    }
}
