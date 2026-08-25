package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.dashboard.DashboardClient;
import com.nhnacademy.insightonfront.adapter.core.dashboard.dto.chart.ChartDataResponse;
import com.nhnacademy.insightonfront.adapter.core.dashboard.dto.dashboard.DashboardResponse;
import com.nhnacademy.insightonfront.adapter.core.dashboard.dto.widget.WidgetSaveRequest;
import com.nhnacademy.insightonfront.adapter.core.location.LocationClient;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationDetailResponse;
import com.nhnacademy.insightonfront.adapter.core.sensor.SensorClient;
import com.nhnacademy.insightonfront.adapter.core.sensor.dto.SensorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;


@Controller
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardClient dashboardClient;
    private final SensorClient sensorClient;
    private final LocationClient locationClient;

    @GetMapping("/my-group/location/{location-id}/dashboard")
    public String getDashboard(
            @CookieValue("groupId") Long groupId,
            @PathVariable("location-id") Long locationId,
                               Model model
    ) {

        // 백엔드 Core 대시보드 구성 조회
        DashboardResponse response = null;
        try {
            response = dashboardClient.getDashboard(groupId, locationId);
        } catch (Exception e) {
            log.warn("[DashboardController] 백엔드 대시보드 데이터 조회 실패: {}", e.getMessage());
        }

        // 백엔드 Core DB의 해당 위치 연결 센서 목록 조회
        List<SensorResponse> sensors = Collections.emptyList();
        try {
            sensors = sensorClient.search(groupId, null, null, locationId, null);
        } catch (Exception e) {
            log.warn("[DashboardController] 백엔드 센서 목록 조회 실패: {}", e.getMessage());
            // 로컬 테스트용 sensor list 하드코딩
//            log.warn("[DashboardController] 백엔드 센서 목록 조회 실패 (테스트 센서 목록 사용): {}", e.getMessage());
//        }
//
//        if (sensors == null || sensors.isEmpty()) {
//            sensors = List.of(
//                    new SensorResponse(1L, 1L, locationId, "DEV_TEMP_01", "테스트 온습도 센서 1", java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now()),
//                    new SensorResponse(2L, 1L, locationId, "DEV_CO2_02", "테스트 CO2 센서 2", java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now()),
//                    new SensorResponse(3L, 1L, locationId, "DEV_PRESS_03", "테스트 기압 센서 3", java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now())
//            );
        }
        LocationDetailResponse location = locationClient.getLocation(groupId, locationId);
      
        model.addAttribute("dashboard", response);
        model.addAttribute("groupId", groupId);
        model.addAttribute("locationId", locationId);
        model.addAttribute("sensors", sensors);
        model.addAttribute("location", location);

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
            log.info("[DashboardController] 대시보드 위젯 저장 성공. groupId: {}, locationId: {}", groupId, locationId);
        } catch (Exception e) {
            log.error("[DashboardController] 대시보드 위젯 저장 실패: {}", e.getMessage());
        }

        return "redirect:/groups/location/" + locationId + "/dashboard";
    }

    @GetMapping("/groups/location/{location-id}/dashboard/widgets/{widget-id}/chart-data")
    @ResponseBody
    public ResponseEntity<ChartDataResponse> getWidgetChartData(
            @CookieValue(value = "groupId", required = false) Long groupId,
            @PathVariable("location-id") Long locationId,
            @PathVariable("widget-id") Long widgetId
    ) {


        try {
            ChartDataResponse chartDataResponse = dashboardClient.getWidgetChartData(groupId, locationId, widgetId);
            return ResponseEntity.ok(chartDataResponse);
        } catch (Exception e) {
            log.error("[DashboardController] InfluxDB 차트 데이터 조회 실패. widgetId: {}", widgetId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
