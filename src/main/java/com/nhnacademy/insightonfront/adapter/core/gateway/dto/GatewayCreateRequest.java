package com.nhnacademy.insightonfront.adapter.core.gateway.dto;

import java.util.Map;

public record GatewayCreateRequest(
        Long groupsId,
        String name,
        ProtocolType protocolType,
        Map<String, Object> connectionConfig
) {
}
