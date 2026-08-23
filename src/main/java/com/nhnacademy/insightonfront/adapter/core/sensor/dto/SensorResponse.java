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
    /** Core와 동일한 기준(최근 5분 이내 통신) - Core가 내려주는 online 필드를 다시 계산한다. */
    public boolean isOnline() {
        return lastSeenAt != null && !lastSeenAt.isBefore(OffsetDateTime.now().minusMinutes(5));
    }
}
