package com.nhnacademy.insightonfront.adapter.core.groupregistration.dto;

import java.time.OffsetDateTime;

public record GroupRegistrationResponse(
        Long groupRegistrationId,
        Long requesterId,
        String groupName,
        String description,
        String groupRegion,
        GroupRegistrationStatus status,
        Long approverId,
        OffsetDateTime createdAt,
        OffsetDateTime approvedAt
) {
}
