package com.nhnacademy.insightonfront.adapter.ai.report;

import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.adapter.ai.report.dto.ReportDetailResponse;
import com.nhnacademy.insightonfront.adapter.ai.report.dto.ReportListResponse;
import com.nhnacademy.insightonfront.adapter.ai.report.dto.ReportType;
import java.time.OffsetDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "insighton-gateway", contextId = "reportClient")
public interface ReportClient {

    @GetMapping("/api/v1/reports")
    PageResponse<ReportListResponse> getReports(@RequestParam("groupId") Long groupId,
                                                @RequestParam(value = "locationId", required = false) Long locationId,
                                                @RequestParam(value = "reportType", required = false) ReportType reportType,
                                                @RequestParam(value = "from", required = false) OffsetDateTime from,
                                                @RequestParam(value = "to", required = false) OffsetDateTime to,
                                                @RequestParam("page") int page,
                                                @RequestParam("size") int size,
                                                @RequestHeader("X-User-Id") Long userId);

    @GetMapping("/api/v1/reports/{reportId}")
    ReportDetailResponse getReport(@PathVariable("reportId") Long reportId,
                                   @RequestHeader("X-User-Id") Long userId);
}
