package com.nhnacademy.insightonfront.domain.telemetrystats.dto;

import java.time.OffsetDateTime;

public record HourlyTelemetryStatViewModel(
        Long hourlyTelemetryStatId,
        String locationName,
        OffsetDateTime logHour,
        String metricsAvg,
        String metricsMax,
        String metricsMin,
        String actuatorOnMinutes,
        OffsetDateTime createdAt
) {
}
