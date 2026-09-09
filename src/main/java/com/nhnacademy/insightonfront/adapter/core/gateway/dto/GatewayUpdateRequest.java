package com.nhnacademy.insightonfront.adapter.core.gateway.dto;

import java.util.Map;

public record GatewayUpdateRequest(
        String name,
        ProtocolType protocolType,
        Map<String, Object> connectionConfig
) {
}
