package com.nhnacademy.insightonfront.adapter.core.groupregistration.dto;

import lombok.Getter;

@Getter
public enum GroupRegistrationStatus {
    PENDING("대기중", "warning"),
    APPROVED("승인됨", "success"),
    REJECTED("거절됨", "danger"),
    CANCELLED("취소됨", "neutral");

    private final String label;
    private final String badgeClass;

    GroupRegistrationStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }
}
