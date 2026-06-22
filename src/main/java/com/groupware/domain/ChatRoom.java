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
    @JoinColumn(name = "team_idx")
    private Team team;

    @Column(name = "room_name", length = 255)
    private String roomName;

    // 이름이 직접 변경됐는지(I-13/후속). 2인 DM도 custom_name이면 사이드바에 커스텀 이름 표시.
    @Column(name = "custom_name")
    private Boolean customName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "del_date")
    private LocalDateTime delDate;
}
