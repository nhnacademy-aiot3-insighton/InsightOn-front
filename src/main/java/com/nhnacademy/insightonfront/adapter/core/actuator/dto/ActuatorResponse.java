package com.nhnacademy.insightonfront.adapter.core.actuator.dto;

import java.time.OffsetDateTime;
import java.util.List;
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
        String externalDeviceId,
        // core가 계산해서 내려주는, 이 공급자+종류로 가능한 SELECT형 명령값.
        // key=stateKey(mode/windDirection), value=중립값 목록. 없으면 panel.html이 타입 기본값으로 폴백.
        Map<String, List<String>> supportedValues
) {
}
