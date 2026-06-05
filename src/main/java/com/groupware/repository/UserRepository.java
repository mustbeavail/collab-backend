package com.groupware.repository;

import com.groupware.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends JpaRepository<User, String> {

    @Query("SELECT u FROM User u WHERE u.withdrwalAt IS NULL AND (u.nick LIKE %:q% OR u.userId LIKE %:q%)")
    List<User> searchByNickOrId(@Param("q") String q);
}
