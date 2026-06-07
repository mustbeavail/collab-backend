package com.groupware.repository;

import com.groupware.domain.VoiceSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceSessionRepository extends JpaRepository<VoiceSession, Long> {
}
