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
}
