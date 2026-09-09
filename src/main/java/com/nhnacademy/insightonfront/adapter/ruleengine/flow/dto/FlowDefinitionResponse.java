package com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowDefinitionResponse(
        Long flowId,
        Long groupId,
        Long locationId,
        String name,
        String description,
        FlowStatus status,
        OffsetDateTime createdAt,
        List<FlowNodeResponse> nodes,
        List<FlowLinkResponse> links
) {
}
