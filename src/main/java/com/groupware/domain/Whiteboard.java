package com.groupware.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "whiteboard")
@Getter @Setter @NoArgsConstructor
public class Whiteboard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "whiteboard_idx", nullable = false)
    private Long whiteboardIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_idx", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "canvas_data", columnDefinition = "longtext")
    private String canvasData;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "del_at")
    private LocalDateTime delAt;
}
