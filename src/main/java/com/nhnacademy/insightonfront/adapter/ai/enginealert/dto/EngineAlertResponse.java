package com.nhnacademy.insightonfront.adapter.ai.enginealert.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record EngineAlertResponse(
        Long engineAlertId,
        Long groupId,
        Long locationId,
        Long flowId,
        String title,
        String message,
        Severity severity,
        Map<String, Object> triggerValue,
        OffsetDateTime createdAt
) {
}
