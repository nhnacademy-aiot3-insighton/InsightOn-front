package com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowLinkResponse(
        Long linkId,
        Long flowId,
        Long sourceNodeId,
        Long targetNodeId,
        String sourcePort,
        String targetPort
) {
}
