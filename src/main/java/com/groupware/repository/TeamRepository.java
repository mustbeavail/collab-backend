package com.groupware.repository;

import com.groupware.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @Query("SELECT DISTINCT tm.team FROM TeamMember tm " +
           "WHERE tm.user.userId = :userId AND tm.exitAt IS NULL AND tm.team.delAt IS NULL")
    List<Team> findActiveTeamsByUserId(@Param("userId") String userId);
}
