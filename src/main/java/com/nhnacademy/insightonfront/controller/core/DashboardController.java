package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.dashboard.DashboardClient;
import com.nhnacademy.insightonfront.adapter.core.dashboard.dto.chart.ChartDataResponse;
import com.nhnacademy.insightonfront.adapter.core.dashboard.dto.dashboard.DashboardResponse;
import com.nhnacademy.insightonfront.adapter.core.dashboard.dto.widget.WidgetSaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardClient dashboardClient;

    @GetMapping("/groups/location/{locationId}/dashboard")
    public String getDashboard(
            @CookieValue(value = "userId", required = false) Long userId,
            @CookieValue(value = "groupId", required = false) Long groupId,
            @PathVariable("locationId") Long locationId,
            Model model
    ) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        DashboardResponse response = dashboardClient.getDashboard(userId, groupId, locationId);

        model.addAttribute("dashboard", response);
        model.addAttribute("groupId", groupId);
        model.addAttribute("locationId", locationId);

        return "dashboard/widgets";
    }

    @PostMapping("/groups/location/{locationId}/dashboard/save")
    public String saveDashboard(
            @CookieValue(value = "userId", required = false) Long userId,
            @CookieValue(value = "groupId", required = false) Long groupId,
            @PathVariable("locationId") Long locationId,
            @RequestBody List<WidgetSaveRequest> requests,
            Model model) {

        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        try {
            Map<Long, ChartDataResponse> responseMap = dashboardClient.saveDashboard(userId, groupId, locationId, requests);

            model.addAttribute("chartData", responseMap);
            log.info("HTML 버튼 클릭으로 Front 컨트롤러 세이브 실행됨!");

        } catch (Exception e) {
            log.error("저장실패!!");
        }
        return "redirect:/groups/location/" + locationId + "/dashboard";
    }
}
