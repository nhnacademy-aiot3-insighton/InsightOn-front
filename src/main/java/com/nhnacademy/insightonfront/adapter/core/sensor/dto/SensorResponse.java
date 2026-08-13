package com.nhnacademy.insightonfront.adapter.core.sensor.dto;

import java.time.OffsetDateTime;

public record SensorResponse(
        Long sensorId,
        Long gatewayId,
        Long locationId,
        String sensorEui,
        String sensorName,
        OffsetDateTime createdAt,
        OffsetDateTime lastSeenAt
) {
}
