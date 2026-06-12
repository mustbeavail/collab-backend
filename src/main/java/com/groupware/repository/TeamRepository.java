package com.groupware.repository;

import com.groupware.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    // 수락(ACTIVE)한 팀만. PENDING 초대는 제외(qa 항목19: 수락 전엔 사이드바에 안 뜨게)
    @Query("SELECT DISTINCT tm.team FROM TeamMember tm " +
           "WHERE tm.user.userId = :userId AND tm.exitAt IS NULL AND tm.team.delAt IS NULL " +
           "AND (tm.status IS NULL OR tm.status = 'ACTIVE')")
    List<Team> findActiveTeamsByUserId(@Param("userId") String userId);
}
