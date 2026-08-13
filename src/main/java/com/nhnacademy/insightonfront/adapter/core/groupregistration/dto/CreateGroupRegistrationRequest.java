package com.nhnacademy.insightonfront.adapter.core.groupregistration.dto;

public record CreateGroupRegistrationRequest(
        String groupName,
        String description,
        String groupRegion
) {
}
