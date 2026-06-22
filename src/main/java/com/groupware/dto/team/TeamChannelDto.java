package com.groupware.dto.team;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TeamChannelDto {
    private Long roomIdx;
    private String roomName;
    private boolean joined;
    private int memberCount; // 활성 멤버 수(I-14: 인원수별 색 구분)
    private boolean owner;    // 현재 사용자가 이 채널의 OWNER인지(I-13: 이름변경 권한)
}
