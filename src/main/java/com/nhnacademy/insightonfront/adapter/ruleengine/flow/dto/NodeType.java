package com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto;

/** Rule Engine의 NodeType을 그대로 미러링한다. */
public enum NodeType {
    SENSOR,
    LOCATION,
    SCHEDULE,
    THRESHOLD,
    TIME_WINDOW,
    EVENT_GATE,
    ACTUATOR_CONTROL,
    ALERT
}
