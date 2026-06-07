package com.groupware.dto.minutes;

import com.groupware.domain.MeetingNote;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class MeetingNoteResponse {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private Long noteIdx;
    private String title;
    private String content;
    private String createdAt;
    private String authorId;
    private String authorNick;

    public static MeetingNoteResponse from(MeetingNote n) {
        return MeetingNoteResponse.builder()
                .noteIdx(n.getNoteIdx())
                .title(n.getTitle())
                .content(n.getContent())
                .createdAt(n.getCreatedAt() != null ? n.getCreatedAt().format(FMT) : null)
                .authorId(n.getUser().getUserId())
                .authorNick(n.getUser().getNick())
                .build();
    }
}
