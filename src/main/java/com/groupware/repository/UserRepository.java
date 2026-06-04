package com.groupware.repository;

import com.groupware.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByMailAdr(String mailAdr);
    boolean existsByMailAdr(String mailAdr);

    @Query("SELECT u FROM User u WHERE u.withdrwalAt IS NULL AND (u.nick LIKE %:q% OR u.mailAdr LIKE %:q%)")
    List<User> searchByNickOrEmail(@Param("q") String q);
}
