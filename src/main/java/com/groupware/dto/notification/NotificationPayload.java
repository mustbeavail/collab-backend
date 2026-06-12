package com.groupware.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationPayload {
    private String type;
    private Long friendIdx;
    private String userId;
    private String nickname;
    private String email;
    private String status;
    private String avatarUrl;
    // 팀 초대 알림(TEAM_INVITE)용
    private Long tmIdx;
    private Long teamIdx;
    private String teamName;
    // 새 메시지 알림(NEW_MESSAGE)용
    private Long roomIdx;
    private String content;
}
