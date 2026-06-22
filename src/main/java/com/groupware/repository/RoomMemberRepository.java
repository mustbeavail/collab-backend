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

    // 방별 활성 멤버 수 배치 조회(I-14: 인원수별 색 구분)
    @Query("SELECT rm.chatRoom.roomIdx, COUNT(rm) FROM RoomMember rm WHERE rm.chatRoom.roomIdx IN :roomIds AND rm.exitAt IS NULL GROUP BY rm.chatRoom.roomIdx")
    List<Object[]> countActiveByRoomIds(@Param("roomIds") List<Long> roomIds);

    // 내가 OWNER인 방 배치 조회(I-13: 이름변경 권한 표시)
    @Query("SELECT rm.chatRoom.roomIdx FROM RoomMember rm WHERE rm.user.userId = :userId AND rm.chatRoom.roomIdx IN :roomIds AND rm.exitAt IS NULL AND rm.role = 'OWNER'")
    List<Long> findOwnedRoomIdxByUserIdAndRoomIds(@Param("userId") String userId, @Param("roomIds") List<Long> roomIds);

    // exitAt 무관(최근 멤버십) — 재가입 시 멤버십 복원용(버그 항목12)
    Optional<RoomMember> findTopByChatRoomAndUserOrderByJoinAtDesc(ChatRoom chatRoom, User user);
}
