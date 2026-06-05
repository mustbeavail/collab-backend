package com.groupware.repository;

import com.groupware.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    List<ChatRoom> findByTeamTeamIdxAndDelDateIsNull(Long teamIdx);
}
