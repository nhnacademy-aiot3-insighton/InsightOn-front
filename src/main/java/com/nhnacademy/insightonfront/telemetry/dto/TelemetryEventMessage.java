package com.nhnacademy.insightonfront.telemetry.dto;

import java.time.Instant;
import java.util.Map;

public record TelemetryEventMessage(
        Long groupId,
        Long locationId,
        Long sensorId,
        Map<String, Object> metrics,
        Instant timestamp
) {
}
