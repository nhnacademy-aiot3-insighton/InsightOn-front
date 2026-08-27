package com.nhnacademy.insightonfront.adapter.ai.telemetrystats;

import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.adapter.ai.telemetrystats.dto.HourlyTelemetryStatResponse;
import java.time.OffsetDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * userId는 안 넘긴다 — 게이트웨이가 Authorization을 검증해서 X-User-Id로 바꿔 AI 서비스에 넘겨준다.
 */
@FeignClient(name = "insighton-gateway", contextId = "hourlyTelemetryStatClient", url = "${service-url.gateway}")
public interface HourlyTelemetryStatClient {

    @GetMapping("/api/v1/hourly-telemetry-stats")
    PageResponse<HourlyTelemetryStatResponse> getHourlyTelemetryStats(@RequestParam("groupId") Long groupId,
                                                                       @RequestParam("locationId") Long locationId,
                                                                       @RequestParam(value = "from", required = false)
                                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                                                       @RequestParam(value = "to", required = false)
                                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
                                                                       @RequestParam("page") int page,
                                                                       @RequestParam("size") int size);
}
