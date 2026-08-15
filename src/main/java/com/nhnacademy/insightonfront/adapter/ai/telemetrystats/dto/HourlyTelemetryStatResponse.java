package com.nhnacademy.insightonfront.adapter.ai.telemetrystats.dto;

import java.time.OffsetDateTime;

public record HourlyTelemetryStatResponse(
        Long hourlyTelemetryStatId,
        Long locationId,
        OffsetDateTime logHour,
        String metricsAvg,
        String metricsMax,
        String metricsMin,
        String actuatorOnMinutes,
        OffsetDateTime createdAt
) {
}
