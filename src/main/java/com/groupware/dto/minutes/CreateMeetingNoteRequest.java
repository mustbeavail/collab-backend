package com.groupware.dto.minutes;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class CreateMeetingNoteRequest {

    @NotBlank
    private String title;

    private String content;
}
