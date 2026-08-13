package com.nhnacademy.insightonfront.adapter.core.actuator.dto;

import java.util.Map;

public record ActuatorRequest(
        Long locationId,
        String sensorName,
        ActuatorType actuatorType,
        Map<String, Object> currentState
) {
}
