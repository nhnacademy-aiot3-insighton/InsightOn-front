package com.nhnacademy.insightonfront.adapter.ai.enginealert;

import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.adapter.ai.enginealert.dto.EngineAlertResponse;
import com.nhnacademy.insightonfront.adapter.ai.enginealert.dto.Severity;
import java.time.OffsetDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "insighton-gateway", contextId = "engineAlertClient")
public interface EngineAlertClient {

    @GetMapping("/api/v1/engine-alerts")
    PageResponse<EngineAlertResponse> getEngineAlerts(@RequestParam("groupId") Long groupId,
                                                       @RequestParam(value = "locationId", required = false) Long locationId,
                                                       @RequestParam(value = "severity", required = false) Severity severity,
                                                       @RequestParam(value = "from", required = false) OffsetDateTime from,
                                                       @RequestParam(value = "to", required = false) OffsetDateTime to,
                                                       @RequestParam("page") int page,
                                                       @RequestParam("size") int size,
                                                       @RequestHeader("X-User-Id") Long userId);

    @GetMapping("/api/v1/engine-alerts/{engineAlertId}")
    EngineAlertResponse getEngineAlert(@PathVariable("engineAlertId") Long engineAlertId,
                                       @RequestHeader("X-User-Id") Long userId);
}
