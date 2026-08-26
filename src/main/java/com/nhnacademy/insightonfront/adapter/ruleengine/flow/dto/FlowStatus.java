package com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto;

/**
 * Rule Engine의 FlowStatus를 그대로 미러링한다.
 * ERROR는 AGENTS.md에는 없지만 실제 Rule Engine enum에 존재한다(노드 문제 발생 시 상태).
 */
public enum FlowStatus {
    ACTIVE,
    INACTIVE,
    ARCHIVED,
    ERROR
}
