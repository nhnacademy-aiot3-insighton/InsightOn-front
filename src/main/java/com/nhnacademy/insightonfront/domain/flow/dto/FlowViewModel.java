package com.nhnacademy.insightonfront.domain.flow.dto;

import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowStatus;
import java.time.OffsetDateTime;

public record FlowViewModel(
        Long flowId,
        String name,
        String description,
        FlowStatus status,
        Long locationId,
        String locationName,
        OffsetDateTime createdAt
) {
}
