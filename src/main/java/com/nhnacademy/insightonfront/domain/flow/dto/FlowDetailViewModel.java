package com.nhnacademy.insightonfront.domain.flow.dto;

import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowLinkResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowNodeResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record FlowDetailViewModel(
        Long flowId,
        String name,
        String description,
        FlowStatus status,
        String locationName,
        OffsetDateTime createdAt,
        List<FlowStepViewModel> steps,
        List<FlowNodeResponse> nodes,
        List<FlowLinkResponse> links
) {
}
