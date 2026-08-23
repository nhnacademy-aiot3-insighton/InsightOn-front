package com.nhnacademy.insightonfront.adapter.core.dashboard;


import com.nhnacademy.insightonfront.adapter.core.dashboard.dto.chart.ChartDataResponse;
import com.nhnacademy.insightonfront.adapter.core.dashboard.dto.dashboard.DashboardResponse;
import com.nhnacademy.insightonfront.adapter.core.dashboard.dto.widget.WidgetSaveRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * userId는 안 넘긴다 — 게이트웨이가 Authorization을 검증해서 X-User-Id로 바꿔 Core에 넘겨준다.
 */
@FeignClient(name = "insighton-gateway", url = "${service-url.gateway}")
public interface DashboardClient {

    /**
     * 대시보드 조회
     */
    @GetMapping("/api/v1/groups/{group-id}/location/{location-id}/dashboard")
    DashboardResponse getDashboard(
            @PathVariable("group-id") Long groupId,
            @PathVariable("location-id") Long locationId
    );

    /**
     * 대시보드 저장 (위젯 생성 및 수정)
     */
    @PostMapping("/api/v1/groups/{group-id}/location/{location-id}/dashboard/save")
    Map<Long, ChartDataResponse> saveDashboard(
            @PathVariable("group-id") Long groupId,
            @PathVariable("location-id") Long locationId,
            @RequestBody List<WidgetSaveRequest> requests
    );


    /**
     * chart.js에서 주기적으로 호출할 API
     */
    @GetMapping("/api/v1/dashboard/widgets/{widget-id}/chart-data")
    ChartDataResponse getWidgetChartData(@PathVariable("widget-id") Long widgetId);
}
