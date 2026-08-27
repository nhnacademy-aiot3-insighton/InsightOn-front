package com.nhnacademy.insightonfront.domain.report.dto;

import java.time.OffsetDateTime;

/**
 * 리포트 기간의 시간별 원자료(hourly_telemetry_stats)를 요약한 값 — 그래프 대신 문장으로
 * 읽을 수 있게 계산해서 보여줌(예: "낮 14시경 최고 온도는 26.2도였고 에어컨은 총 3.5시간 가동됨")
 */
public record ReportTelemetryHighlightViewModel(
        boolean hasData,
        Double peakTemperature,
        OffsetDateTime peakTemperatureHour,
        double aircondOnHours,
        boolean hasPoorHumidity,
        OffsetDateTime firstPoorHumidityHour,
        double airPurifierOnHours
) {
}
