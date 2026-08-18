package com.nhnacademy.insightonfront.adapter.core.actuator.dto;

import java.util.Set;

/** core의 ActuatorCommandPreset 규칙과 동일한 모양 — 프론트는 이 규칙으로 조작 화면의 입력 위젯만 고른다. */
public sealed interface CommandValueRule {
    record AllowedValues(Set<String> values) implements CommandValueRule {}
    record NumericRange(int min, int max) implements CommandValueRule {}
}
