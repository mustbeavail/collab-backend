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
    @JoinColumn(name = "room_idx")
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_idx")
    private Team team;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "participants", length = 500)
    private String participants;

    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "appointment_date")
    private LocalDateTime appointmentDate;

    @Column(name = "location", length = 100)
    private String location;

    // 위도 ±90, 경도 ±180 — 정수부 3자리 필요(경도)하므로 precision 11, scale 8
    @Column(name = "lat", precision = 11, scale = 8)
    private BigDecimal lat;

    @Column(name = "longt", precision = 11, scale = 8)
    private BigDecimal longt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "del_at")
    private LocalDateTime delAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
