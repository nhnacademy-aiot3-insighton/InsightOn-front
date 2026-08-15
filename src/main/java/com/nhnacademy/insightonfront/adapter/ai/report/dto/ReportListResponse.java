package com.nhnacademy.insightonfront.adapter.ai.report.dto;

import java.time.OffsetDateTime;

public record ReportListResponse(
        Long reportId,
        Long groupId,
        Long locationId,
        String title,
        ReportType reportType,
        OffsetDateTime createdAt
) {
}
