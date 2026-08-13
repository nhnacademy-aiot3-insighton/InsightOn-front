package com.nhnacademy.insightonfront.adapter.core.sensor.dto;

public record SensorUpdateRequest(
        Long locationId,
        String sensorName
) {
}
