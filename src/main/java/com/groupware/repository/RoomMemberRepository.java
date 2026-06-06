package com.groupware.repository;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.RoomMember;
import com.groupware.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    boolean existsByChatRoomAndUserAndExitAtIsNull(ChatRoom chatRoom, User user);

    Optional<RoomMember> findByChatRoomAndUserAndExitAtIsNull(ChatRoom chatRoom, User user);

    @Query("SELECT rm FROM RoomMember rm JOIN FETCH rm.user WHERE rm.chatRoom = :room AND rm.exitAt IS NULL")
    List<RoomMember> findAllActiveByRoom(@Param("room") ChatRoom room);

    @Query("SELECT rm.chatRoom.roomIdx FROM RoomMember rm WHERE rm.user.userId = :userId AND rm.chatRoom.roomIdx IN :roomIds AND rm.exitAt IS NULL")
    List<Long> findJoinedRoomIdxByUserIdAndRoomIds(@Param("userId") String userId, @Param("roomIds") List<Long> roomIds);
}
