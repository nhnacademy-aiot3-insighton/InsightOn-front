package com.nhnacademy.insightonfront.dto.chart;

import java.util.List;

/**
 * 차트 전체 응답 DTO
 *
 * @param labels   x축 [예시 : "10:00", "10:15", "10:30"]
 * @param datasets y축 데이터선들의 리스트
 */
public record ChartDataResponse(
        List<String> labels,
        List<ChartDataset> datasets
) {
}

