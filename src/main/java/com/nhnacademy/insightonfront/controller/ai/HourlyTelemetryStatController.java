package com.nhnacademy.insightonfront.controller.ai;

import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.domain.telemetrystats.dto.HourlyTelemetryStatViewModel;
import com.nhnacademy.insightonfront.domain.telemetrystats.service.HourlyTelemetryStatViewService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/groups/hourly-telemetry-stats")
public class HourlyTelemetryStatController {

    private final HourlyTelemetryStatViewService hourlyTelemetryStatViewService;

    // locationId는 AI 서비스 API 자체에서 필수 파라미터라 여기도 필수로 받는다.
    @GetMapping
    @ResponseBody
    public PageResponse<HourlyTelemetryStatViewModel> getHourlyTelemetryStats(@CookieValue(value = "groupId", required = false) Long groupId,
                                                                               @RequestParam Long locationId,
                                                                               @RequestParam(required = false) OffsetDateTime from,
                                                                               @RequestParam(required = false) OffsetDateTime to,
                                                                               @RequestParam(defaultValue = "0") int page,
                                                                               @RequestParam(defaultValue = "20") int size) {
        if (groupId == null) {
            throw new IllegalArgumentException("소속된 그룹이 없습니다.");
        }

        return hourlyTelemetryStatViewService.getHourlyTelemetryStats(groupId, locationId, from, to, page, size);
    }
}
