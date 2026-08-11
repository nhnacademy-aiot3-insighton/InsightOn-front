package com.nhnacademy.insightonfront.dto.chart;

import java.util.List;

/**
 * 차트 데이터셋 DTO
 *
 * @param label field 이름 (예시 -> "temperature", "humidity")
 * @param data  측정 값 배열 ([23.5, 23.8, 24.1])
 */
public record ChartDataset(
        String label,
        List<Object> data
) {
}
