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
        OffsetDateTime createdAt,
        ControlProvider controlProvider, // null이면 미연결(UNBOUND) - 제어 요청이 거절됨
        String externalDeviceId
) {
}
