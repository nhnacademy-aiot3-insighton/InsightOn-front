package com.nhnacademy.insightonfront.domain.flow.dto;

import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowLinkResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowNodeResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowStatus;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.NodeType;
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
    public boolean hasCondition() {
        return steps.stream().anyMatch(step -> "조건".equals(step.roleLabel()));
    }

    public boolean hasEventGate() {
        return steps.stream().anyMatch(step -> step.nodeType() == NodeType.EVENT_GATE);
    }

    public int eventGateStepNumber() {
        return hasCondition() ? 3 : 2;
    }

    public int laneCount() {
        return 2 + (hasCondition() ? 1 : 0) + (hasEventGate() ? 1 : 0);
    }
}
