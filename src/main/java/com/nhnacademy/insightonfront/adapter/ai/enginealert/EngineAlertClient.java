package com.nhnacademy.insightonfront.adapter.ai.enginealert;

import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.adapter.ai.enginealert.dto.EngineAlertResponse;
import com.nhnacademy.insightonfront.adapter.ai.enginealert.dto.Severity;
import java.time.OffsetDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * userId는 안 넘긴다 — 게이트웨이가 Authorization을 검증해서 X-User-Id로 바꿔 AI 서비스에 넘겨준다.
 */
@FeignClient(name = "insighton-gateway", contextId = "engineAlertClient", url = "${service-url.gateway}")
public interface EngineAlertClient {

    @GetMapping("/api/v1/engine-alerts")
    PageResponse<EngineAlertResponse> getEngineAlerts(@RequestParam("groupId") Long groupId,
                                                       @RequestParam(value = "locationId", required = false) Long locationId,
                                                       @RequestParam(value = "severity", required = false) Severity severity,
                                                       @RequestParam(value = "from", required = false) OffsetDateTime from,
                                                       @RequestParam(value = "to", required = false) OffsetDateTime to,
                                                       @RequestParam("page") int page,
                                                       @RequestParam("size") int size);

    @GetMapping("/api/v1/engine-alerts/{engineAlertId}")
    EngineAlertResponse getEngineAlert(@PathVariable("engineAlertId") Long engineAlertId);
}
