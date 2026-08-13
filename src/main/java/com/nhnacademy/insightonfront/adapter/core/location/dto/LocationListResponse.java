package com.nhnacademy.insightonfront.adapter.core.location.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationListResponse(
        Long locationId,
        String locationName,
        AutoControlMode autoControlMode
) {
}
