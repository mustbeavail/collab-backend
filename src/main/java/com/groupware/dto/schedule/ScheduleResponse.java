package com.groupware.dto.schedule;

import com.groupware.domain.Schedule;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class ScheduleResponse {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private Long scheduleIdx;
    private String title;
    private String date;   // "yyyy-MM-dd'T'HH:mm"
    private List<String> participants;
    private String content;
    private String location;
    private BigDecimal lat;
    private BigDecimal lng;
    private String creatorId;
    private String creatorNick;

    public static ScheduleResponse from(Schedule s) {
        List<String> parts = (s.getParticipants() == null || s.getParticipants().isBlank())
                ? Collections.emptyList()
                : Arrays.stream(s.getParticipants().split(","))
                        .map(String::trim)
                        .filter(p -> !p.isEmpty())
                        .collect(Collectors.toList());

        return ScheduleResponse.builder()
                .scheduleIdx(s.getScheduleIdx())
                .title(s.getTitle())
                .date(s.getAppointmentDate() != null ? s.getAppointmentDate().format(FMT) : null)
                .participants(parts)
                .content(s.getContent())
                .location(s.getLocation())
                .lat(s.getLat())
                .lng(s.getLongt())
                .creatorId(s.getUser().getUserId())
                .creatorNick(s.getUser().getNick())
                .build();
    }
}
