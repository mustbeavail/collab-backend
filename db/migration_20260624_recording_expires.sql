-- [I] 음성/화상 채팅 녹음 파일 관리 (항목 20·21)
-- files 테이블에 expires_at(만료일시) 추가.
--  · 일반 파일: expires_at = NULL (영구 보관)
--  · 녹음 파일: expires_at = 업로드시각 + 30일, file_type = 'RECORDING'
-- 만료된 녹음은 스케줄러(RecordingCleanupService)가 일일 배치로 삭제.
ALTER TABLE `files`
  ADD COLUMN `expires_at` datetime DEFAULT NULL AFTER `created_at`;

-- 만료 녹음 정리 배치 조회 성능용 인덱스(선택)
CREATE INDEX `idx_files_expires_at` ON `files` (`expires_at`);
