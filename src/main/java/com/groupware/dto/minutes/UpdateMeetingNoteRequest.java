package com.groupware.dto.minutes;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UpdateMeetingNoteRequest {

    @NotBlank
    private String title;

    private String content;
}
