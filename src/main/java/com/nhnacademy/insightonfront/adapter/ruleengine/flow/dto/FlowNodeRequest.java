package com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto;

import java.util.Map;

public record FlowNodeRequest(
        String clientNodeKey,
        NodeType nodeType,
        Map<String, Object> configuration
) {
}
