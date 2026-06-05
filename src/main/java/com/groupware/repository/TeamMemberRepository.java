package com.groupware.repository;

import com.groupware.domain.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    @Query("SELECT tm FROM TeamMember tm JOIN FETCH tm.user WHERE tm.team.teamIdx = :teamIdx AND tm.exitAt IS NULL")
    List<TeamMember> findActiveByTeamIdx(@Param("teamIdx") Long teamIdx);

    @Query("SELECT tm FROM TeamMember tm WHERE tm.team.teamIdx = :teamIdx AND tm.user.userId = :userId AND tm.exitAt IS NULL")
    Optional<TeamMember> findActiveByTeamIdxAndUserId(@Param("teamIdx") Long teamIdx, @Param("userId") String userId);
}
