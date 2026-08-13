package com.nhnacademy.insightonfront.adapter.core.gateway.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record GatewayResponse(
        Long id,
        Long groupsId,
        String name,
        ProtocolType protocolType,
        GatewayStatus status,
        OffsetDateTime lastHeartbeatAt,
        OffsetDateTime createdAt,
        Map<String, Object> connectionConfig
) {
}
