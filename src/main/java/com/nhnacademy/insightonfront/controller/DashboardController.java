package com.nhnacademy.insightonfront.controller;

import com.nhnacademy.insightonfront.client.core.CoreApiClient;
import com.nhnacademy.insightonfront.dto.chart.ChartDataResponse;
import com.nhnacademy.insightonfront.dto.dashboard.DashboardResponse;
import com.nhnacademy.insightonfront.dto.widget.WidgetSaveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
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
            List<WidgetSaveRequest> requests,
            Model model) {

        Map<Long, ChartDataResponse> responseMap = coreApiClient.saveDashboard(userId, groupId, locationId, requests);

        model.addAttribute("chartData", responseMap);
        return "redirect:/dashboard/widgets";
    }
}
