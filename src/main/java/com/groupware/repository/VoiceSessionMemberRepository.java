package com.groupware.repository;

import com.groupware.domain.VoiceSessionMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceSessionMemberRepository extends JpaRepository<VoiceSessionMember, Long> {
}
