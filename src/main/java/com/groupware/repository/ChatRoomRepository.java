package com.groupware.repository;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    List<ChatRoom> findByTeamTeamIdxAndDelDateIsNull(Long teamIdx);

    @Query("""
        SELECT r FROM ChatRoom r
        WHERE r.team IS NULL AND r.delDate IS NULL
          AND EXISTS (SELECT m FROM RoomMember m WHERE m.chatRoom = r AND m.user = :u1 AND m.exitAt IS NULL)
          AND EXISTS (SELECT m FROM RoomMember m WHERE m.chatRoom = r AND m.user = :u2 AND m.exitAt IS NULL)
          AND (SELECT COUNT(m) FROM RoomMember m WHERE m.chatRoom = r AND m.exitAt IS NULL) = 2
        """)
    Optional<ChatRoom> findDmRoom(@Param("u1") User u1, @Param("u2") User u2);
}
