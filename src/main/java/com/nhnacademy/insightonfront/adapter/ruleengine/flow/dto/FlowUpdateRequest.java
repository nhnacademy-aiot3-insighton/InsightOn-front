package com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto;

import java.util.List;

public record FlowUpdateRequest(
        String name,
        String description,
        List<FlowNodeRequest> nodes,
        List<FlowLinkRequest> links
) {
}
