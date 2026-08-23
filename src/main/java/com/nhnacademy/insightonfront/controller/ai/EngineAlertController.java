package com.nhnacademy.insightonfront.controller.ai;

import com.nhnacademy.insightonfront.adapter.ai.enginealert.dto.Severity;
import com.nhnacademy.insightonfront.adapter.core.location.LocationClient;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationListResponse;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.domain.enginealert.dto.EngineAlertViewModel;
import com.nhnacademy.insightonfront.domain.enginealert.service.EngineAlertViewService;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.CookieValue;

/**
 * 엔진 알람(Rule Engine이 조건을 만족했을 때 남기는 알람 로그) 조회. groupId는 쿠키에서 읽는다
 * — 한 유저는 그룹 하나에만 속해서 groupId를 URL에 실을 필요가 없다.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/engine-alerts")
public class EngineAlertController {

    private final EngineAlertViewService engineAlertViewService;
    private final LocationClient locationClient;

    @GetMapping
    public String list(@CookieValue(value = "userId", required = false) Long userId,
                        @CookieValue(value = "groupId", required = false) Long groupId,
                        @RequestParam(required = false) Long locationId,
                        @RequestParam(required = false) Severity severity,
                        @RequestParam(required = false) OffsetDateTime from,
                        @RequestParam(required = false) OffsetDateTime to,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        PageResponse<EngineAlertViewModel> alerts =
                engineAlertViewService.getEngineAlerts(groupId, locationId, severity, from, to, page, size);
        List<LocationListResponse> locations = locationClient.getLocationList(groupId);

        model.addAttribute("alerts", alerts);
        model.addAttribute("locations", locations);
        model.addAttribute("selectedLocationId", locationId);
        model.addAttribute("selectedSeverity", severity);
        return "enginealert/list";
    }

    @GetMapping("/{engine-alert-id}")
    public String detail(@CookieValue(value = "userId", required = false) Long userId,
                          @CookieValue(value = "groupId", required = false) Long groupId,
                          @PathVariable("engine-alert-id") Long engineAlertId,
                          Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        EngineAlertViewModel alert = engineAlertViewService.getEngineAlert(engineAlertId);
        model.addAttribute("alert", alert);
        return "enginealert/detail";
    }
}
