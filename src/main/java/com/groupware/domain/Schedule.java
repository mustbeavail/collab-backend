package com.groupware.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule")
@Getter @Setter @NoArgsConstructor
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_idx", nullable = false)
    private Long scheduleIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_idx", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_idx", nullable = false)
    private Team team;

    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "appointment_date")
    private LocalDateTime appointmentDate;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "lat", precision = 10, scale = 8)
    private BigDecimal lat;

    @Column(name = "longt", precision = 10, scale = 8)
    private BigDecimal longt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "del_at")
    private LocalDateTime delAt;
}
