package com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto;

public record FlowLinkRequest(
        String sourceClientNodeKey,
        String targetClientNodeKey,
        String sourcePort,
        String targetPort
) {
}
