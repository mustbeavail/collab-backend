package com.groupware.dto.chart;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ChartAnalyzeRequest {

    @NotNull
    private Long roomIdx;

    @NotEmpty
    private List<List<Object>> tableData;

    private String question;
}
