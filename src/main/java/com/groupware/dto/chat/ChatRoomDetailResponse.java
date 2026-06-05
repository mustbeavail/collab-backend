package com.groupware.dto.chat;

import com.groupware.domain.ChatRoom;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter @Builder
public class ChatRoomDetailResponse {
    private Long roomIdx;
    private String roomName;
    private String description;
    private LocalDateTime createdAt;
    private boolean isDm;

    public static ChatRoomDetailResponse from(ChatRoom room) {
        return ChatRoomDetailResponse.builder()
                .roomIdx(room.getRoomIdx())
                .roomName(room.getRoomName())
                .description(room.getDescription())
                .createdAt(room.getCreatedAt())
                .isDm(room.getTeam() == null)
                .build();
    }
}
