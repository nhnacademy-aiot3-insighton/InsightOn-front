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

/**
 * groupId는 쿠키에서 읽는다(한 유저 = 한 그룹). locationId는 한 그룹 안에서도 여러 위치를
 * 오갈 수 있어 세션이 아니라 경로 변수로 받는다.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardClient dashboardClient;

    @GetMapping("/my-group/location/{location-id}/dashboard")
    public String getDashboard(
            @CookieValue("groupId") Long groupId,
            @PathVariable("location-id") Long locationId,
                               Model model
    ) {

        DashboardResponse response = dashboardClient.getDashboard(groupId, locationId);

        model.addAttribute("dashboard", response);

        return "dashboard/widgets";
    }

    @PostMapping("/my-group/location/{location-id}/dashboard/save")
    public String saveDashboard(
            @CookieValue("groupId") Long groupId,
            @PathVariable("location-id") Long locationId,
            @RequestBody List<WidgetSaveRequest> requests,
            Model model) {


        try {
            Map<Long, ChartDataResponse> responseMap = dashboardClient.saveDashboard(groupId, locationId, requests);

        model.addAttribute("chartData", responseMap);
            log.info("HTML 버튼 클릭으로 Front 컨트롤러 세이브 실행됨!");

        } catch (Exception e) {
            log.error("저장실패!!");
        }
        return "redirect:/my-group/location/" + locationId + "/dashboard";
    }
}
