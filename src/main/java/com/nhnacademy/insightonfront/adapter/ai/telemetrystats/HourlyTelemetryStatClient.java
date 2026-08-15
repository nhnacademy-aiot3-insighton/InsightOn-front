package com.nhnacademy.insightonfront.adapter.ai.telemetrystats;

import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.adapter.ai.telemetrystats.dto.HourlyTelemetryStatResponse;
import java.time.OffsetDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "insighton-gateway", contextId = "hourlyTelemetryStatClient", url = "${service-url.gateway}")
public interface HourlyTelemetryStatClient {

    @GetMapping("/api/v1/hourly-telemetry-stats")
    PageResponse<HourlyTelemetryStatResponse> getHourlyTelemetryStats(@RequestParam("groupId") Long groupId,
                                                                       @RequestParam("locationId") Long locationId,
                                                                       @RequestParam(value = "from", required = false) OffsetDateTime from,
                                                                       @RequestParam(value = "to", required = false) OffsetDateTime to,
                                                                       @RequestParam("page") int page,
                                                                       @RequestParam("size") int size,
                                                                       @RequestHeader("X-User-Id") Long userId);
}
