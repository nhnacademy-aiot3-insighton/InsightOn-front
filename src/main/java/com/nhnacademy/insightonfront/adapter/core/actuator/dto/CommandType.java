package com.nhnacademy.insightonfront.adapter.core.actuator.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// core의 CommandType을 그대로 미러링한다 — stateKey가 실제로 core에 보내는/currentState에 저장되는 키
@Getter
@RequiredArgsConstructor
public enum CommandType {
    POWER_STATUS("power"),
    OPERATION_MODE("mode"),
    WIND_DIRECTION("windDirection"),
    SET_TEMPERATURE("temperature");

    private final String stateKey;
}
