package com.groupware.dto.search;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RoomSearchResponse {

    private List<MessageResult> messages;
    private List<FileResult> files;
    private List<ScheduleResult> schedules;
    private List<NoteResult> notes;

    @Getter
    @Builder
    public static class MessageResult {
        private Long msgIdx;
        private String content;
        private String senderNick;
        private String sentAt;
    }

    @Getter
    @Builder
    public static class FileResult {
        private Long fileIdx;
        private String filename;
        private Long fileSize;
        private String uploaderNick;
        private String uploadedAt;
    }

    @Getter
    @Builder
    public static class ScheduleResult {
        private Long scheduleIdx;
        private String title;
        private String date;
    }

    @Getter
    @Builder
    public static class NoteResult {
        private Long noteIdx;
        private String title;
        private String creatorNick;
        private String createdAt;
    }
}
