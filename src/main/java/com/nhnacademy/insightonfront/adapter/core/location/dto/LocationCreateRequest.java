package com.nhnacademy.insightonfront.adapter.core.location.dto;

public record LocationCreateRequest(
        String locationName,
        AutoControlMode autoControlMode
) {
}
