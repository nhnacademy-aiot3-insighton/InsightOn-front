package com.nhnacademy.insightonfront.adapter.ai.notification.dto;

import java.time.OffsetDateTime;

public record DashboardNotificationResponse(
        Long dashboardNotificationId,
        Long locationId,
        NotificationType notificationType,
        Long sourceId,
        String title,
        boolean isRead,
        OffsetDateTime createdAt
) {
}
