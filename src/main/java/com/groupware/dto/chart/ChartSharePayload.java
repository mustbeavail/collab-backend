package com.groupware.dto.chart;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChartSharePayload {

    private String fromUserId;
    private String fromNickname;
    private ChartAnalyzeResponse chartConfig;
}
