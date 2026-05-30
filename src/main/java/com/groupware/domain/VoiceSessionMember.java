package com.groupware.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "voice_session_members")
@Getter @Setter @NoArgsConstructor
public class VoiceSessionMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vsm_idx", nullable = false)
    private Long vsmIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_idx", nullable = false)
    private VoiceSession voiceSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "join_at")
    private LocalDateTime joinAt;

    @Column(name = "exit_at")
    private LocalDateTime exitAt;
}
