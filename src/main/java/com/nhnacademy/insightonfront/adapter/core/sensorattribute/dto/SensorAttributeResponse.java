package com.nhnacademy.insightonfront.adapter.core.sensorattribute.dto;

public record SensorAttributeResponse(
        String metricKey,
        String displayName,
        String unit
) {
}
