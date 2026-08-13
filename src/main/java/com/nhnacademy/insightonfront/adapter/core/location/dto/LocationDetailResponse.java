package com.nhnacademy.insightonfront.adapter.core.location.dto;

import java.time.OffsetDateTime;

public record LocationDetailResponse(
        Long locationId,
        Long groupId,
        String locationName,
        OffsetDateTime createdAt,
        AutoControlMode autoControlMode
) {
}
