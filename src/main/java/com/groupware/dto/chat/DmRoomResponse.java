package com.groupware.dto.chat;

import lombok.Builder;
import lombok.Getter;

/** 내 DM/그룹 채팅방 목록 항목(qa 항목15 — 사이드바 채팅방 목록 표시). */
@Getter
@Builder
public class DmRoomResponse {
    private Long roomIdx;
    private String name;          // 1:1 DM이면 상대 닉네임, 그룹이면 방 이름
    private boolean dm;           // 1:1 여부(참여자 2명)
    private String targetUserId;  // DM 상대 userId(그룹이면 null)
    private String avatarUrl;     // DM 상대 프로필 사진(그룹이면 null)
}
