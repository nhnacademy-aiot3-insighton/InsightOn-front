package com.nhnacademy.insightonfront.adapter.core.actuator.dto;

import java.time.OffsetDateTime;

public record ActuatorRunLogResponse(
        Long runLogId,
        Long actuatorId,
        CommandType commandType,
        String commandValue,
        ExecutedByType executedByType,
        Long executedByUserId,
        OffsetDateTime executedAt
) {
}
