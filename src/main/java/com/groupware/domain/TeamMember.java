package com.groupware.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "team_members")
@Getter @Setter @NoArgsConstructor
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tm_idx", nullable = false)
    private Long tmIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_idx", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "role", length = 20)
    private String role;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "join_at")
    private LocalDateTime joinAt;

    @Column(name = "exit_at")
    private LocalDateTime exitAt;
}
