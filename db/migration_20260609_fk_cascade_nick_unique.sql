-- ============================================================
-- 마이그레이션 2026-06-09 : 재가입 안정화 + 닉네임 유일성
--   대상 DB: collab_test (운영 DB도 동일 적용)
--
-- [내용]
--   1) users.user_id 를 참조하는 모든 자식 FK에 ON UPDATE CASCADE 추가
--      → AuthService.renameWithdrawnUser 의 user_id rename(재가입) 시
--        자식 데이터(user_id / friend_id / guest_id)가 자동 갱신되어
--        FK 제약 위반 없이 탈퇴 회원 재가입이 가능해진다.
--   2) users.nick 에 UNIQUE 제약 추가(닉네임 중복 방지).
--      탈퇴 회원은 nick=NULL 로 비워 재사용 가능하게 한다
--      (UNIQUE 는 NULL 다중 허용).
--
-- [실행]
--   mysql -u <user> -p collab_test < db/migration_20260609_fk_cascade_nick_unique.sql
--
-- [주의]
--   - 코드 배포 전에 이 마이그레이션을 먼저 적용할 것.
--   - DROP / ADD FOREIGN KEY 는 같은 이름이라도 별도 ALTER 문으로 나눈다.
--     (한 ALTER 에서 동시에 하면 MariaDB 가 errno 121 "Duplicate key" 로 거부함)
--   - 2)의 ADD UNIQUE 는 "활성 회원 간 닉네임 중복"이 이미 있으면 실패한다.
--     실패 시 중복 닉네임을 먼저 정리한 뒤 재실행할 것.
-- ============================================================

-- ── 1) 자식 FK에 ON UPDATE CASCADE ─────────────────────────
-- (ON DELETE 는 기존 동작 유지: 기본 RESTRICT)

ALTER TABLE `files` DROP FOREIGN KEY `files_ibfk_1`;
ALTER TABLE `files` ADD CONSTRAINT `files_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE;

ALTER TABLE `friends` DROP FOREIGN KEY `friends_ibfk_1`;
ALTER TABLE `friends` ADD CONSTRAINT `friends_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE;

ALTER TABLE `friends` DROP FOREIGN KEY `friends_ibfk_2`;
ALTER TABLE `friends` ADD CONSTRAINT `friends_ibfk_2` FOREIGN KEY (`friend_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE;

ALTER TABLE `meeting_notes` DROP FOREIGN KEY `meeting_notes_ibfk_2`;
ALTER TABLE `meeting_notes` ADD CONSTRAINT `meeting_notes_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE;

ALTER TABLE `messages` DROP FOREIGN KEY `messages_ibfk_2`;
ALTER TABLE `messages` ADD CONSTRAINT `messages_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE;

ALTER TABLE `notification` DROP FOREIGN KEY `notification_ibfk_1`;
ALTER TABLE `notification` ADD CONSTRAINT `notification_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE;

ALTER TABLE `room_members` DROP FOREIGN KEY `room_members_ibfk_1`;
ALTER TABLE `room_members` ADD CONSTRAINT `room_members_ibfk_1` FOREIGN KEY (`guest_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE;

ALTER TABLE `schedule` DROP FOREIGN KEY `schedule_ibfk_1`;
ALTER TABLE `schedule` ADD CONSTRAINT `schedule_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE;

ALTER TABLE `shorthand` DROP FOREIGN KEY `shorthand_ibfk_1`;
ALTER TABLE `shorthand` ADD CONSTRAINT `shorthand_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE;

ALTER TABLE `team_members` DROP FOREIGN KEY `team_members_ibfk_2`;
ALTER TABLE `team_members` ADD CONSTRAINT `team_members_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE;

ALTER TABLE `voice_session` DROP FOREIGN KEY `voice_session_ibfk_2`;
ALTER TABLE `voice_session` ADD CONSTRAINT `voice_session_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE;

ALTER TABLE `voice_session_members` DROP FOREIGN KEY `voice_session_members_ibfk_2`;
ALTER TABLE `voice_session_members` ADD CONSTRAINT `voice_session_members_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE;

ALTER TABLE `whiteboard` DROP FOREIGN KEY `whiteboard_ibfk_2`;
ALTER TABLE `whiteboard` ADD CONSTRAINT `whiteboard_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE;

-- ── 2) 닉네임 유일성 ───────────────────────────────────────
-- 기존 탈퇴 회원의 닉네임을 비워 재사용 가능하게 한 뒤 UNIQUE 추가
UPDATE `users` SET `nick` = NULL WHERE `withdrawal_at` IS NOT NULL;

ALTER TABLE `users` ADD UNIQUE KEY `uk_users_nick` (`nick`);
