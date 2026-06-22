-- ============================================================
-- Collab DB 스키마 (database: collab_test)
-- mysqldump --no-data 기반 / 최종 갱신: 2026-06-09
--
-- [목적] 새 환경 구축 시 이 파일로 테이블을 먼저 생성한다.
--   백엔드는 spring.jpa.hibernate.ddl-auto=validate 이므로,
--   부팅 전에 이 스키마가 DB에 존재해야 한다(없으면 부팅 실패).
--
-- [사용법]
--   CREATE DATABASE collab_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   mysql -u <user> -p collab_test < db/schema.sql
--
-- [규칙] JPA 엔티티 변경 시 이 파일도 반드시 함께 갱신한다.
--
-- [2026-06-09 반영] users.user_id 참조 자식 FK에 ON UPDATE CASCADE 설정
--   (탈퇴 회원 재가입 시 user_id rename → 자식 자동 갱신). users.nick UNIQUE 추가
--   (탈퇴 회원은 nick=NULL 로 비워 재사용 허용). 기존 DB 적용은
--   db/migration_20260609_fk_cascade_nick_unique.sql 참조.
-- ============================================================

/*M!999999\- enable the sandbox mode */

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `chat_rooms`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_rooms` (
  `room_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `team_idx` bigint(20) DEFAULT NULL,
  `room_name` varchar(255) DEFAULT NULL,
  `custom_name` tinyint(1) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `del_date` datetime DEFAULT NULL,
  PRIMARY KEY (`room_idx`),
  KEY `chat_rooms_ibfk_1` (`team_idx`),
  CONSTRAINT `chat_rooms_ibfk_1` FOREIGN KEY (`team_idx`) REFERENCES `teams` (`team_idx`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `chat_summaries`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_summaries` (
  `summ_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `room_idx` bigint(20) NOT NULL,
  `content` text DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  PRIMARY KEY (`summ_idx`),
  KEY `room_idx` (`room_idx`),
  CONSTRAINT `chat_summaries_ibfk_1` FOREIGN KEY (`room_idx`) REFERENCES `chat_rooms` (`room_idx`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `files`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `files` (
  `file_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` varchar(254) NOT NULL,
  `room_id` bigint(20) NOT NULL,
  `new_filename` varchar(40) NOT NULL,
  `ori_filename` varchar(100) DEFAULT NULL,
  `file_path` varchar(255) DEFAULT NULL,
  `file_type` varchar(10) DEFAULT NULL,
  `file_extension` varchar(10) DEFAULT NULL,
  `file_size` bigint(20) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`file_idx`),
  KEY `room_id` (`room_id`),
  KEY `files_ibfk_1` (`user_id`),
  CONSTRAINT `files_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE,
  CONSTRAINT `files_ibfk_2` FOREIGN KEY (`room_id`) REFERENCES `chat_rooms` (`room_idx`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `friends`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `friends` (
  `friend_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` varchar(254) NOT NULL,
  `friend_id` varchar(254) NOT NULL,
  `status` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`friend_idx`),
  KEY `friends_ibfk_1` (`user_id`),
  KEY `friends_ibfk_2` (`friend_id`),
  CONSTRAINT `friends_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE,
  CONSTRAINT `friends_ibfk_2` FOREIGN KEY (`friend_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `meeting_notes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `meeting_notes` (
  `note_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `room_idx` bigint(20) NOT NULL,
  `user_id` varchar(254) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `content` text DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `del_at` datetime DEFAULT NULL,
  PRIMARY KEY (`note_idx`),
  KEY `room_idx` (`room_idx`),
  KEY `meeting_notes_ibfk_2` (`user_id`),
  CONSTRAINT `meeting_notes_ibfk_1` FOREIGN KEY (`room_idx`) REFERENCES `chat_rooms` (`room_idx`),
  CONSTRAINT `meeting_notes_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `messages` (
  `msg_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `room_idx` bigint(20) NOT NULL,
  `user_id` varchar(254) NOT NULL,
  `content` varchar(6000) DEFAULT NULL,
  `msg_type` varchar(20) DEFAULT NULL,
  `sent_at` datetime DEFAULT NULL,
  `del_yn` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`msg_idx`),
  KEY `room_idx` (`room_idx`),
  KEY `messages_ibfk_2` (`user_id`),
  CONSTRAINT `messages_ibfk_1` FOREIGN KEY (`room_idx`) REFERENCES `chat_rooms` (`room_idx`),
  CONSTRAINT `messages_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification` (
  `noti_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` varchar(254) NOT NULL,
  `ref_idx` bigint(20) DEFAULT NULL,
  `ref_type` varchar(40) DEFAULT NULL,
  `message` varchar(200) DEFAULT NULL,
  `is_read` tinyint(1) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  PRIMARY KEY (`noti_idx`),
  KEY `notification_ibfk_1` (`user_id`),
  CONSTRAINT `notification_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `room_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `room_members` (
  `rm_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `guest_id` varchar(254) NOT NULL,
  `room_idx` bigint(20) NOT NULL,
  `role` varchar(20) DEFAULT NULL,
  `join_at` datetime DEFAULT NULL,
  `exit_at` datetime DEFAULT NULL,
  PRIMARY KEY (`rm_idx`),
  KEY `room_idx` (`room_idx`),
  KEY `room_members_ibfk_1` (`guest_id`),
  CONSTRAINT `room_members_ibfk_1` FOREIGN KEY (`guest_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE,
  CONSTRAINT `room_members_ibfk_2` FOREIGN KEY (`room_idx`) REFERENCES `chat_rooms` (`room_idx`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule` (
  `schedule_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` varchar(254) NOT NULL,
  `room_idx` bigint(20) DEFAULT NULL,
  `team_idx` bigint(20) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `participants` varchar(500) DEFAULT NULL,
  `content` varchar(500) DEFAULT NULL,
  `appointment_date` datetime DEFAULT NULL,
  `location` varchar(100) DEFAULT NULL,
  `lat` decimal(11,8) DEFAULT NULL,
  `longt` decimal(11,8) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `del_at` datetime DEFAULT NULL,
  PRIMARY KEY (`schedule_idx`),
  KEY `room_idx` (`room_idx`),
  KEY `team_idx` (`team_idx`),
  KEY `schedule_ibfk_1` (`user_id`),
  CONSTRAINT `schedule_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE,
  CONSTRAINT `schedule_ibfk_2` FOREIGN KEY (`room_idx`) REFERENCES `chat_rooms` (`room_idx`),
  CONSTRAINT `schedule_ibfk_3` FOREIGN KEY (`team_idx`) REFERENCES `teams` (`team_idx`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `shorthand`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `shorthand` (
  `shorthand_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` varchar(254) NOT NULL,
  `room_idx` bigint(20) NOT NULL,
  `content` text DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `del_at` datetime DEFAULT NULL,
  PRIMARY KEY (`shorthand_idx`),
  KEY `room_idx` (`room_idx`),
  KEY `shorthand_ibfk_1` (`user_id`),
  CONSTRAINT `shorthand_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE,
  CONSTRAINT `shorthand_ibfk_2` FOREIGN KEY (`room_idx`) REFERENCES `chat_rooms` (`room_idx`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `team_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `team_members` (
  `tm_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `team_idx` bigint(20) NOT NULL,
  `user_id` varchar(254) NOT NULL,
  `role` varchar(20) DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL,
  `join_at` datetime DEFAULT NULL,
  `exit_at` datetime DEFAULT NULL,
  PRIMARY KEY (`tm_idx`),
  KEY `team_idx` (`team_idx`),
  KEY `team_members_ibfk_2` (`user_id`),
  CONSTRAINT `team_members_ibfk_1` FOREIGN KEY (`team_idx`) REFERENCES `teams` (`team_idx`),
  CONSTRAINT `team_members_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `teams`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `teams` (
  `team_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `team_name` varchar(255) DEFAULT NULL,
  `about` varchar(6000) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `del_at` datetime DEFAULT NULL,
  PRIMARY KEY (`team_idx`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `user_id` varchar(254) NOT NULL,
  `pw` varchar(100) DEFAULT NULL,
  `nick` varchar(40) DEFAULT NULL,
  `about` varchar(6000) DEFAULT NULL,
  `join_at` datetime DEFAULT NULL,
  `withdrawal_at` datetime DEFAULT NULL,
  `avatar_url` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_users_nick` (`nick`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `voice_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `voice_session` (
  `session_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `room_idx` bigint(20) NOT NULL,
  `user_id` varchar(254) NOT NULL,
  `session_type` varchar(10) DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `end_at` datetime DEFAULT NULL,
  PRIMARY KEY (`session_idx`),
  KEY `room_idx` (`room_idx`),
  KEY `voice_session_ibfk_2` (`user_id`),
  CONSTRAINT `voice_session_ibfk_1` FOREIGN KEY (`room_idx`) REFERENCES `chat_rooms` (`room_idx`),
  CONSTRAINT `voice_session_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `voice_session_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `voice_session_members` (
  `vsm_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `session_idx` bigint(20) NOT NULL,
  `user_id` varchar(254) NOT NULL,
  `join_at` datetime DEFAULT NULL,
  `exit_at` datetime DEFAULT NULL,
  PRIMARY KEY (`vsm_idx`),
  KEY `session_idx` (`session_idx`),
  KEY `voice_session_members_ibfk_2` (`user_id`),
  CONSTRAINT `voice_session_members_ibfk_1` FOREIGN KEY (`session_idx`) REFERENCES `voice_session` (`session_idx`),
  CONSTRAINT `voice_session_members_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `whiteboard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `whiteboard` (
  `whiteboard_idx` bigint(20) NOT NULL AUTO_INCREMENT,
  `room_idx` bigint(20) NOT NULL,
  `user_id` varchar(254) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `canvas_data` longtext DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `del_at` datetime DEFAULT NULL,
  PRIMARY KEY (`whiteboard_idx`),
  KEY `room_idx` (`room_idx`),
  KEY `whiteboard_ibfk_2` (`user_id`),
  CONSTRAINT `whiteboard_ibfk_1` FOREIGN KEY (`room_idx`) REFERENCES `chat_rooms` (`room_idx`),
  CONSTRAINT `whiteboard_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
