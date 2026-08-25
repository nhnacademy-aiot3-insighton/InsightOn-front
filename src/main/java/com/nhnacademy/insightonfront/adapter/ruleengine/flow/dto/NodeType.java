package com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto;

/**
 * Rule Engine의 NodeType을 그대로 미러링한다.
 * SCHEDULE/TIME_WINDOW/TIMER/ACTUATOR_CONTROL/EXTERNAL_NOTIFICATION은 enum엔 있지만
 * 아직 실행기가 없어 활성화는 안 된다(백엔드팀 전달 스펙 7번 참고).
 */
public enum NodeType {
    SENSOR,
    LOCATION,
    SCHEDULE,
    THRESHOLD,
    TIME_WINDOW,
    TIMER,
    ACTUATOR_CONTROL,
    ALERT,
    EXTERNAL_NOTIFICATION
}
