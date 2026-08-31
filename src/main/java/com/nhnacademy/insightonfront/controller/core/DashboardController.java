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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


import feign.FeignException;
import com.nhnacademy.insightonfront.common.service.GroupPermissionService;


@Controller
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardClient dashboardClient;
    private final SensorClient sensorClient;
    private final LocationClient locationClient;
    private final GroupPermissionService groupPermissionService;

    @GetMapping("/my-group/location/{location-id}/dashboard")
    public String getDashboard(
            @CookieValue(value = "userId", required = false) Long userId,
            @CookieValue(value = "groupId", required = false) Long groupId,
            @PathVariable("location-id") Long locationId,
            Model model
    ) {

        // 백엔드 Core 대시보드 구성 조회
        DashboardResponse response = null;
        try {
            response = dashboardClient.getDashboard(groupId, locationId);
        } catch (FeignException.NotFound e) {
            log.info("[DashboardController] 아직 생성되지 않은 신규 위치 대시보드입니다. locationId: {}", locationId);
        } catch (Exception e) {
            log.warn("[DashboardController] 백엔드 대시보드 데이터 조회 실패: {}", e.getMessage());
        }

        // 백엔드 Core 대시보드 구성 조회
        LocationDetailResponse location = null;
        try {
            location = locationClient.getLocation(groupId, locationId);
        } catch (Exception e) {
            log.warn("[DashboardController] 백엔드 Location 데이터 조회 실패: {}", e.getMessage());
        }

        // 백엔드 Core DB의 해당 위치 연결 센서 목록 조회
        List<SensorResponse> sensors = Collections.emptyList();
        try {
            sensors = sensorClient.search(groupId, null, null, locationId, null);
        } catch (Exception e) {
            log.warn("[DashboardController] 백엔드 센서 목록 조회 실패: {}", e.getMessage());
        }

        boolean canManage = false;
        if (groupId != null && userId != null) {
            canManage = groupPermissionService.isManagerOrAbove(groupId, userId);
        }

        model.addAttribute("dashboard", response);
        model.addAttribute("locationId", locationId);
        model.addAttribute("location", location);
        model.addAttribute("groupId", groupId);
        model.addAttribute("sensors", sensors);
        model.addAttribute("canManage", canManage);

        return "dashboard/widgets";
    }

    @PostMapping("/my-group/location/{location-id}/dashboard/save")
    @ResponseBody
    public ResponseEntity<List<Long>> saveDashboard(
            @CookieValue(value = "userId", required = false) Long userId,
            @CookieValue(value = "groupId", required = false) Long groupId,
            @PathVariable("location-id") Long locationId,
            @RequestBody List<WidgetSaveRequest> requests) {

        try {
            if (groupId != null && userId != null && !groupPermissionService.isManagerOrAbove(groupId, userId)) {
                log.warn("[DashboardController] MEMBER 계정의 위젯 저장 요청 차단. groupId: {}, userId: {}", groupId, userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Map<Long, ChartDataResponse> responseMap = dashboardClient.saveDashboard(groupId, locationId, requests);
            List<Long> widgetIds = new ArrayList<>(responseMap.keySet());
            log.info("[DashboardController] 대시보드 위젯 저장 성공. groupId: {}, locationId: {}, widgetIds: {}", groupId, locationId, widgetIds);
            return ResponseEntity.ok(widgetIds);
        } catch (FeignException.Conflict e) {
            log.warn("[DashboardController] 대시보드 위젯 저장 충돌(409). 이미 수정되었거나 삭제된 위젯입니다. locationId: {}", locationId);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            log.error("[DashboardController] 대시보드 위젯 저장 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
        } catch (FeignException.NotFound e) {
            log.info("[DashboardController] 초기 위젯 차트 데이터 준비 중 (또는 데이터 없음). widgetId: {}", widgetId);
            return ResponseEntity.ok(new ChartDataResponse(Collections.emptyList(), Collections.emptyList()));
        } catch (Exception e) {
            log.warn("[DashboardController] InfluxDB 차트 데이터 조회 실패. widgetId: {}, message: {}", widgetId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
