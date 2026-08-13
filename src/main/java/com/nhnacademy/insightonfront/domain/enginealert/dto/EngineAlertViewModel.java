package com.nhnacademy.insightonfront.domain.enginealert.dto;

import com.nhnacademy.insightonfront.adapter.ai.enginealert.dto.Severity;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * flowId는 아직 이름을 붙이지 못한다 — Rule Engine에 flow 이름 조회 API가 없어 FlowNameResolver가
 * 없는 상태(frontend-bff-name-resolution-design 문서 참고, Rule Engine 쪽에 별도 요청 예정).
 */
public record EngineAlertViewModel(
        Long engineAlertId,
        String locationName,
        Long flowId,
        String title,
        String message,
        Severity severity,
        Map<String, Object> triggerValue,
        OffsetDateTime createdAt
) {
}
