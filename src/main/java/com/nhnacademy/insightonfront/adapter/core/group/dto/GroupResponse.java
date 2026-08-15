package com.nhnacademy.insightonfront.adapter.core.group.dto;

import java.time.OffsetDateTime;

public record GroupResponse(
        Long groupId,
        String name,
        String description,
        String groupRegion,
        String inviteToken,
        OffsetDateTime createdAt
) {
}
