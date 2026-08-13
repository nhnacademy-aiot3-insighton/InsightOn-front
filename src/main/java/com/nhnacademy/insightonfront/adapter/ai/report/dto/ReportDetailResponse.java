package com.nhnacademy.insightonfront.adapter.ai.report.dto;

import java.time.OffsetDateTime;

public record ReportDetailResponse(
        Long reportId,
        Long groupId,
        Long locationId,
        String title,
        ReportType reportType,
        String content,
        OffsetDateTime createdAt
) {
}
