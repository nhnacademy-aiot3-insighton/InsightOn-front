package com.nhnacademy.insightonfront.adapter.core.actuator.dto;

import java.util.Map;

public record ActuatorRequest(
        Long locationId,
        String sensorName,
        ActuatorType actuatorType,
        Map<String, Object> currentState,
        ControlProvider controlProvider // 지정하면 core가 external_device_id를 자동 생성한다
) {
}
