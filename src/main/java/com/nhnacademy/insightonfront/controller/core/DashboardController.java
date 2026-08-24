package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.dashboard.DashboardClient;
import com.nhnacademy.insightonfront.adapter.core.dashboard.dto.chart.ChartDataResponse;
import com.nhnacademy.insightonfront.adapter.core.dashboard.dto.dashboard.DashboardResponse;
import com.nhnacademy.insightonfront.adapter.core.dashboard.dto.widget.WidgetSaveRequest;
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

/**
 * 위치별 위젯 대시보드 컨트롤러 (운영 반영 버전)
 * <p>
 * [주요 변경 및 적용 내용]:
 * 1. @CookieValue를 사용한 사용자(userId) 및 그룹(groupId) 권한 파라미터 수신 (쿠키 없을 시 /login 리다이렉트)
 * 2. REST 아키텍처 원칙에 맞춰 locationId를 URL 경로(@PathVariable)로 바인딩
 * 3. 실제 Core 백엔드 서비스(DashboardClient, SensorClient)와 연동하여 DB 기반 센서 및 대시보드 데이터 바인딩
 * 4. 더미 센서 목록 및 테스트용 기본값(1L) 제거 완료
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardClient dashboardClient;
    private final SensorClient sensorClient;

    /**
     * 위치별 대시보드 및 연결 센서 목록 조회
     */
    @GetMapping("/groups/location/{location-id}/dashboard")
    public String getDashboard(
            @CookieValue(value = "userId", required = false) Long userId,
            @CookieValue(value = "groupId", required = false) Long groupId,
            @PathVariable("location-id") Long locationId,
            Model model
    ) {
        // [보안 검증]: 쿠키 미존재(비인가 사용자) 시 로그인 페이지로 즉시 리다이렉트
        if (userId == null || groupId == null) {
            log.warn("[DashboardController] 미인가 사용자의 대시보드 접근 시도 (쿠키 미존재). 로그인 페이지로 리다이렉트.");
            return "redirect:/login";
        }

        // 백엔드 Core 대시보드 구성 조회
        DashboardResponse response = null;
        try {
            response = dashboardClient.getDashboard(userId, groupId, locationId);
        } catch (Exception e) {
            log.warn("[DashboardController] 백엔드 대시보드 데이터 조회 실패: {}", e.getMessage());
        }

        // 백엔드 Core DB의 해당 위치 연결 센서 목록 조회
        List<SensorResponse> sensors = Collections.emptyList();
        try {
            sensors = sensorClient.search(userId, groupId, null, null, locationId, null);
        } catch (Exception e) {
            log.warn("[DashboardController] 백엔드 센서 목록 조회 실패: {}", e.getMessage());
        }

        model.addAttribute("dashboard", response);
        model.addAttribute("groupId", groupId);
        model.addAttribute("locationId", locationId);
        model.addAttribute("sensors", sensors);

        return "dashboard/widgets";
    }

    /**
     * 대시보드 위젯 구성 및 레이아웃 배치 저장
     */
    @PostMapping("/groups/location/{location-id}/dashboard/save")
    public String saveDashboard(
            @CookieValue(value = "userId", required = false) Long userId,
            @CookieValue(value = "groupId", required = false) Long groupId,
            @PathVariable("location-id") Long locationId,
            @RequestBody List<WidgetSaveRequest> requests,
            Model model) {

        // [보안 검증]: 쿠키 미존재(비인가 사용자) 시 로그인 페이지로 리다이렉트
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        try {
            Map<Long, ChartDataResponse> responseMap = dashboardClient.saveDashboard(userId, groupId, locationId, requests);
            model.addAttribute("chartData", responseMap);
            log.info("[DashboardController] 대시보드 위젯 저장 성공. userId: {}, groupId: {}, locationId: {}", userId, groupId, locationId);
        } catch (Exception e) {
            log.error("[DashboardController] 대시보드 위젯 저장 실패: {}", e.getMessage());
        }

        return "redirect:/groups/location/" + locationId + "/dashboard";
    }

    @GetMapping("/groups/location/{location-id}/dashboard/widgets/{widget-id}/chart-data")
    @ResponseBody
    public ResponseEntity<ChartDataResponse> getWidgetChartData(
            @CookieValue(value = "userId", required = false) Long userId,
            @CookieValue(value = "groupId", required = false) Long groupId,
            @PathVariable("location-id") Long locationId,
            @PathVariable("widget-id") Long widgetId
    ) {
        if (userId == null || groupId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ChartDataResponse chartDataResponse = dashboardClient.getWidgetChartData(userId, groupId, locationId, widgetId);
            return ResponseEntity.ok(chartDataResponse);
        } catch (Exception e) {
            log.error("[DashboardController] InfluxDB 차트 데이터 조회 실패. widgetId: {}", widgetId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
