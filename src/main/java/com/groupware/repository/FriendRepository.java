package com.groupware.repository;

import com.groupware.domain.Friend;
import com.groupware.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    @Query("SELECT f FROM Friend f JOIN FETCH f.user JOIN FETCH f.friend WHERE (f.user = :user OR f.friend = :user) AND f.status = 'ACCEPTED'")
    List<Friend> findAcceptedFriends(@Param("user") User user);

    @Query("SELECT f FROM Friend f JOIN FETCH f.user WHERE f.friend = :friend AND f.status = :status")
    List<Friend> findByFriendAndStatus(@Param("friend") User friend, @Param("status") String status);

    @Query("SELECT COUNT(f) > 0 FROM Friend f WHERE (f.user = :u1 AND f.friend = :u2) OR (f.user = :u2 AND f.friend = :u1)")
    boolean existsRelationship(@Param("u1") User u1, @Param("u2") User u2);

    Optional<Friend> findByFriendIdxAndFriend(Long friendIdx, User friend);

    @Query("SELECT f FROM Friend f WHERE f.friendIdx = :idx AND (f.user = :user OR f.friend = :user) AND f.status = 'ACCEPTED'")
    Optional<Friend> findAcceptedByIdxAndUser(@Param("idx") Long idx, @Param("user") User user);
}
