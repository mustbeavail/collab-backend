package com.groupware.dto.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class UpdateScheduleRequest {

    @NotBlank
    private String title;

    @NotNull
    private LocalDateTime date;

    private List<String> participants;

    private String content;

    private String location;

    private BigDecimal lat;

    private BigDecimal lng;
}
