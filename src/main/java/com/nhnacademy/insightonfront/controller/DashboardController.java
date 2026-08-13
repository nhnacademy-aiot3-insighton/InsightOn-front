package com.nhnacademy.insightonfront.controller;

import com.nhnacademy.insightonfront.client.core.CoreApiClient;
import com.nhnacademy.insightonfront.dto.chart.ChartDataResponse;
import com.nhnacademy.insightonfront.dto.dashboard.DashboardResponse;
import com.nhnacademy.insightonfront.dto.widget.WidgetSaveRequest;
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

    private final CoreApiClient coreApiClient;

    @GetMapping("/groups/{group-id}/location/{location-id}/dashboard")
    public String getDashboard(
            //여기서 auth 토큰을 넘겨줘야함(?)
            @RequestHeader Long userId,
            @PathVariable("group-id") Long groupId,
            @PathVariable("location-id") Long locationId,
                               Model model
    ) {

        DashboardResponse response = coreApiClient.getDashboard(userId, groupId, locationId);

        model.addAttribute("dashboard", response);

        return "dashboard/widgets";
    }

    @PostMapping("/groups/{group-id}/location/{location-id}/dashboard/save")
    public String saveDashboard(
            @RequestHeader Long userId,
            @PathVariable("group-id") Long groupId,
            @PathVariable("location-id") Long locationId,
            @RequestBody List<WidgetSaveRequest> requests,
            Model model) {


        try {
            Map<Long, ChartDataResponse> responseMap = coreApiClient.saveDashboard(userId, groupId, locationId, requests);

        model.addAttribute("chartData", responseMap);
            log.info("HTML 버튼 클릭으로 Front 컨트롤러 세이브 실행됨!");

        } catch (Exception e) {
            log.error("저장실패!!");
        }
        return "redirect:/testSave";
    }
}
