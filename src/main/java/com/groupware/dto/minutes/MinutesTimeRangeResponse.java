package com.groupware.dto.minutes;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 항목2(일정이후): AI 회의록 시간범위 디폴트.
 * 방의 가장 오래된/최신 메시지 시각을 "yyyy-MM-ddTHH:mm"(datetime-local 호환)으로 반환.
 * 메시지가 없으면 start/end 모두 null.
 */
@Getter
@Builder
public class MinutesTimeRangeResponse {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final String start;
    private final String end;

    public static MinutesTimeRangeResponse of(LocalDateTime start, LocalDateTime end) {
        return MinutesTimeRangeResponse.builder()
                .start(start != null ? start.format(FMT) : null)
                .end(end != null ? end.format(FMT) : null)
                .build();
    }
}
