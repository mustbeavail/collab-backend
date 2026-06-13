-- 2026-06-13 일정 위치 좌표 저장 버그 수정
-- 원인: schedule.lat/longt 가 decimal(10,8) → 정수부 2자리 한계.
--   경도(longitude)는 한국 기준 124~132 등 정수부 3자리라 저장 시
--   "Out of range value for column 'longt'" (SQLSyntaxError) → 위치 일정 생성 500.
-- 수정: 위도 ±90, 경도 ±180(정수부 3자리) 수용하도록 precision 11, scale 8 로 확대.
ALTER TABLE `schedule`
    MODIFY COLUMN `lat`   decimal(11,8) DEFAULT NULL,
    MODIFY COLUMN `longt` decimal(11,8) DEFAULT NULL;
