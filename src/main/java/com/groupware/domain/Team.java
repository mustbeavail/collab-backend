package com.groupware.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "teams")
@Getter @Setter @NoArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_idx", nullable = false)
    private Long teamIdx;

    @Column(name = "team_name", length = 255)
    private String teamName;

    @Column(name = "about", length = 6000)
    private String about;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "del_at")
    private LocalDateTime delAt;
}
