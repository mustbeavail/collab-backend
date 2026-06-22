-- 2026-06-22 채팅방 이름변경 플래그
-- 2인 DM도 이름을 직접 변경(custom)했으면 사이드바에 커스텀 이름을 표시하기 위해
-- chat_rooms.custom_name 플래그 추가. 기존 행은 NULL(=비커스텀, 상대 닉네임 표시).
ALTER TABLE `chat_rooms`
    ADD COLUMN `custom_name` tinyint(1) DEFAULT NULL AFTER `room_name`;
