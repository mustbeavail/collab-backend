package com.groupware.dto.chart;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ChartAnalyzeResponse {

    private String chartType;
    private List<String> labels;
    private List<ChartDataset> datasets;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ChartDataset {
        private String label;
        private List<Object> data;
        private Object backgroundColor;
        private Object borderColor;
        private Integer borderWidth;
    }
}
