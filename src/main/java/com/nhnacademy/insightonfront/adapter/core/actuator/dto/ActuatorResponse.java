package com.nhnacademy.insightonfront.adapter.core.actuator.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ActuatorResponse(
        Long actuatorId,
        Long locationId,
        String sensorName,
        ActuatorType actuatorType,
        Map<String, Object> currentState,
        OffsetDateTime stateUpdatedAt,
        OffsetDateTime createdAt
) {
}
