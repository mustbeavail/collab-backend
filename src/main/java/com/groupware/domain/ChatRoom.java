package com.groupware.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms")
@Getter @Setter @NoArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_idx", nullable = false)
    private Long roomIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_idx", nullable = false)
    private Team team;

    @Column(name = "room_name", length = 255)
    private String roomName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "del_date")
    private LocalDateTime delDate;
}
