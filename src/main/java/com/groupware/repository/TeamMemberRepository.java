package com.groupware.repository;

import com.groupware.domain.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    // 탈퇴(withdrawalAt != null)한 사용자는 팀 멤버 목록에서 제외(버그 항목6)
    @Query("SELECT tm FROM TeamMember tm JOIN FETCH tm.user WHERE tm.team.teamIdx = :teamIdx AND tm.exitAt IS NULL AND (tm.status IS NULL OR tm.status = 'ACTIVE') AND tm.user.withdrawalAt IS NULL")
    List<TeamMember> findActiveByTeamIdx(@Param("teamIdx") Long teamIdx);

    // 특정 사용자와 같은 팀에 속한 다른 멤버들의 userId(상태 브로드캐스트 대상, 버그 항목20)
    @Query("SELECT DISTINCT tm2.user.userId FROM TeamMember tm1, TeamMember tm2 " +
           "WHERE tm1.team = tm2.team AND tm1.user.userId = :userId AND tm2.user.userId <> :userId " +
           "AND tm1.exitAt IS NULL AND tm2.exitAt IS NULL " +
           "AND (tm1.status IS NULL OR tm1.status = 'ACTIVE') AND (tm2.status IS NULL OR tm2.status = 'ACTIVE') " +
           "AND tm2.user.withdrawalAt IS NULL")
    List<String> findTeamCoMemberUserIds(@Param("userId") String userId);

    @Query("SELECT tm FROM TeamMember tm WHERE tm.team.teamIdx = :teamIdx AND tm.user.userId = :userId AND tm.exitAt IS NULL AND (tm.status IS NULL OR tm.status = 'ACTIVE')")
    Optional<TeamMember> findActiveByTeamIdxAndUserId(@Param("teamIdx") Long teamIdx, @Param("userId") String userId);

    @Query("SELECT tm FROM TeamMember tm WHERE tm.team.teamIdx = :teamIdx AND tm.user.userId = :userId AND tm.exitAt IS NULL")
    Optional<TeamMember> findCurrentByTeamIdxAndUserId(@Param("teamIdx") Long teamIdx, @Param("userId") String userId);

    // 삭제된 팀(delAt != null)의 초대는 제외(초대→팀삭제 시 죽은 초대 숨김)
    @Query("SELECT tm FROM TeamMember tm JOIN FETCH tm.team WHERE tm.user.userId = :userId AND tm.status = 'PENDING' AND tm.exitAt IS NULL AND tm.team.delAt IS NULL")
    List<TeamMember> findPendingInvitationsByUserId(@Param("userId") String userId);

    // 사용자가 현재 LEADER로 있는 활성 팀 멤버십(탈퇴 시 자동 위임 대상 팀 찾기)
    @Query("SELECT tm FROM TeamMember tm JOIN FETCH tm.team WHERE tm.user.userId = :userId " +
           "AND tm.role = 'LEADER' AND tm.exitAt IS NULL AND (tm.status IS NULL OR tm.status = 'ACTIVE')")
    List<TeamMember> findActiveLeaderMembershipsByUserId(@Param("userId") String userId);
}
