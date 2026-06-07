package com.groupware.dto.search;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GlobalSearchResponse {

    private List<FriendResult> friends;
    private List<TeamResult> teams;
    private List<RoomResult> rooms;

    @Getter
    @Builder
    public static class FriendResult {
        private String userId;
        private String nickname;
        private String avatarUrl;
    }

    @Getter
    @Builder
    public static class TeamResult {
        private Long teamIdx;
        private String teamName;
    }

    @Getter
    @Builder
    public static class RoomResult {
        private Long roomIdx;
        private String roomName;
        private boolean isDm;
    }
}
