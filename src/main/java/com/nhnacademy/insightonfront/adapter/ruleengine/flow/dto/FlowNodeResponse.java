package com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowNodeResponse(
        Long nodeId,
        NodeType nodeType,
        Map<String, Object> configuration
) {
}
