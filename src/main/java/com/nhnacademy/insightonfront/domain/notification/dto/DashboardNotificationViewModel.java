package com.nhnacademy.insightonfront.domain.notification.dto;

import com.nhnacademy.insightonfront.adapter.ai.notification.dto.NotificationType;
import java.time.OffsetDateTime;

/**
 * sourceId는 이름으로 바꾸지 않는다 — 클릭 시 이동할 원본(리포트/알람/제안) 상세 페이지의 링크를
 * 만드는 용도라 화면에 그대로 노출되지 않는다.
 */
public record DashboardNotificationViewModel(
        Long dashboardNotificationId,
        String locationName,
        NotificationType notificationType,
        Long sourceId,
        String title,
        boolean isRead,
        OffsetDateTime createdAt
) {
}
