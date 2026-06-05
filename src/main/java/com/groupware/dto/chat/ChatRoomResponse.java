package com.groupware.dto.chat;

import com.groupware.domain.ChatRoom;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatRoomResponse {

    private Long roomIdx;
    private String roomName;
    private Long teamIdx;

    public static ChatRoomResponse from(ChatRoom room) {
        return ChatRoomResponse.builder()
                .roomIdx(room.getRoomIdx())
                .roomName(room.getRoomName())
                .teamIdx(room.getTeam() != null ? room.getTeam().getTeamIdx() : null)
                .build();
    }
}
