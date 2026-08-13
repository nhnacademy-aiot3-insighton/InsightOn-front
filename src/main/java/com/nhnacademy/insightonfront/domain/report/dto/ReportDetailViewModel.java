package com.nhnacademy.insightonfront.domain.report.dto;

import com.nhnacademy.insightonfront.adapter.ai.report.dto.ReportType;
import java.time.OffsetDateTime;

public record ReportDetailViewModel(
        Long reportId,
        String locationName,
        String title,
        ReportType reportType,
        String content,
        OffsetDateTime createdAt
) {
}
