package com.groupware.dto.user;

import com.groupware.domain.User;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class UserProfileResponse {

    private String userId;
    private String email;
    private String nickname;
    private String about;
    private String avatarUrl;
    private LocalDateTime joinAt;
    // 탈퇴 회원이면 true — 프론트에서 메일/자기소개 등 일절 미표시(I-5)
    private boolean withdrawn;
    // 다른 회원 정보보기에서 친구 추가 버튼 표시용(I-1): NONE/SELF/FRIEND/SENT/RECEIVED
    private String friendStatus;

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .email(user.getUserId())
                .nickname(user.getNick())
                .about(user.getAbout())
                .avatarUrl(user.getAvatarUrl())
                .joinAt(user.getJoinAt())
                .withdrawn(false)
                .build();
    }

    /** 탈퇴 회원: 식별 불가능하도록 민감정보(메일/자기소개/사진/가입일) 일절 제외(I-5). */
    public static UserProfileResponse withdrawn(User user) {
        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .email(null)
                .nickname("(탈퇴한 회원)")
                .about(null)
                .avatarUrl(null)
                .joinAt(null)
                .withdrawn(true)
                .friendStatus("NONE")
                .build();
    }
}
