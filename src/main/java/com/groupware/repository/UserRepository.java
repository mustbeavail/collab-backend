package com.groupware.repository;

import com.groupware.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends JpaRepository<User, String> {

    @Query("SELECT u FROM User u WHERE u.withdrawalAt IS NULL AND (u.nick LIKE %:q% OR u.userId LIKE %:q%)")
    List<User> searchByNickOrId(@Param("q") String q);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.nick = :nick AND u.withdrawalAt IS NULL AND u.userId != :excludeUserId")
    boolean existsNickByOtherUser(@Param("nick") String nick, @Param("excludeUserId") String excludeUserId);

    boolean existsByUserIdAndWithdrawalAtIsNull(String userId);

    boolean existsByNickAndWithdrawalAtIsNull(String nick);
}
