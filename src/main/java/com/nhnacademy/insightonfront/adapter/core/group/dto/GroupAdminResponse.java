package com.nhnacademy.insightonfront.adapter.core.group.dto;

public record GroupAdminResponse(
        Long groupId,
        String name,
        String description,
        String groupRegion
) {
}
