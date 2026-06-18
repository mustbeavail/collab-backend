package com.groupware.dto.chat;

import com.groupware.domain.ChatRoom;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter @Builder
public class ChatRoomDetailResponse {
    private Long roomIdx;
    private String roomName;
    private LocalDateTime createdAt;
    private boolean isDm;
    private Long teamIdx;   // 팀 채팅방이면 팀 식별자, 아니면 null

    public static ChatRoomDetailResponse from(ChatRoom room) {
        return ChatRoomDetailResponse.builder()
                .roomIdx(room.getRoomIdx())
                .roomName(room.getRoomName())
                .createdAt(room.getCreatedAt())
                .isDm(room.getTeam() == null)
                .teamIdx(room.getTeam() != null ? room.getTeam().getTeamIdx() : null)
                .build();
    }
}
