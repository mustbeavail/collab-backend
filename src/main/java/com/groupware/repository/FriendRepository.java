package com.groupware.repository;

import com.groupware.domain.Friend;
import com.groupware.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    // 상대방이 탈퇴(withdrawalAt != null)한 친구는 목록에서 제외(버그 항목6)
    @Query("SELECT f FROM Friend f JOIN FETCH f.user JOIN FETCH f.friend " +
           "WHERE (f.user = :user OR f.friend = :user) AND f.status = 'ACCEPTED' " +
           "AND ((f.user = :user AND f.friend.withdrawalAt IS NULL) " +
           "  OR (f.friend = :user AND f.user.withdrawalAt IS NULL))")
    List<Friend> findAcceptedFriends(@Param("user") User user);

    // 요청 보낸 상대가 탈퇴(withdrawalAt != null)했으면 받은 요청 목록에서 제외(죽은 요청 숨김)
    @Query("SELECT f FROM Friend f JOIN FETCH f.user WHERE f.friend = :friend AND f.status = :status AND f.user.withdrawalAt IS NULL")
    List<Friend> findByFriendAndStatus(@Param("friend") User friend, @Param("status") String status);

    // 내가 보낸(상대 응답 대기) 요청 — 대상(friend) 정보 fetch, 탈퇴한 대상은 제외
    @Query("SELECT f FROM Friend f JOIN FETCH f.friend WHERE f.user = :user AND f.status = :status AND f.friend.withdrawalAt IS NULL")
    List<Friend> findByUserAndStatus(@Param("user") User user, @Param("status") String status);

    @Query("SELECT COUNT(f) > 0 FROM Friend f WHERE (f.user = :u1 AND f.friend = :u2) OR (f.user = :u2 AND f.friend = :u1)")
    boolean existsRelationship(@Param("u1") User u1, @Param("u2") User u2);

    // 두 유저 사이의 모든 친구행(요청/수락, 양방향) — 시연 봇 관계 정리용
    @Query("SELECT f FROM Friend f WHERE (f.user = :u1 AND f.friend = :u2) OR (f.user = :u2 AND f.friend = :u1)")
    List<Friend> findRelationships(@Param("u1") User u1, @Param("u2") User u2);

    Optional<Friend> findByFriendIdxAndFriend(Long friendIdx, User friend);

    Optional<Friend> findByUserAndFriend(User user, User friend);

    @Query("SELECT f FROM Friend f WHERE f.friendIdx = :idx AND (f.user = :user OR f.friend = :user) AND f.status = 'ACCEPTED'")
    Optional<Friend> findAcceptedByIdxAndUser(@Param("idx") Long idx, @Param("user") User user);
}
