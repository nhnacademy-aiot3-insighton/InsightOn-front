package com.nhnacademy.insightonfront.adapter.ai.notification;

import com.nhnacademy.insightonfront.adapter.ai.notification.dto.DashboardNotificationResponse;
import com.nhnacademy.insightonfront.adapter.ai.notification.dto.NotificationType;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 안 읽은 알림 목록 조회(페이지네이션 없음)와 읽음 처리만 다룬다.
 * <p>{@code GET /api/v1/dashboard-notifications/stream}(SSE 실시간 구독)은 이 클라이언트에 없음 —
 * Feign은 블로킹 클라이언트라 응답이 안 끝나는 SSE 스트림을 그대로 소비하는 데 안 맞는다.
 * 실시간 알림은 브라우저가 Gateway의 SSE 엔드포인트에 직접 EventSource로 붙거나, WebClient 기반
 * 별도 컴포넌트로 처리해야 한다.
 * <p>userId는 안 넘긴다 — 게이트웨이가 Authorization을 검증해서 X-User-Id로 바꿔 넘겨준다.
 */
@FeignClient(name = "insighton-gateway", contextId = "dashboardNotificationClient", url = "${service-url.gateway}")
public interface DashboardNotificationClient {

    @GetMapping("/api/v1/dashboard-notifications")
    List<DashboardNotificationResponse> getUnreadNotifications(@RequestParam("groupId") Long groupId);

    @PostMapping("/api/v1/dashboard-notifications/{dashboardNotificationId}/read")
    DashboardNotificationResponse markAsRead(@PathVariable("dashboardNotificationId") Long dashboardNotificationId);

    @GetMapping("/api/v1/dashboard-notifications/search")
    PageResponse<DashboardNotificationResponse> search(@RequestParam("groupId") Long groupId,
                                                        @RequestParam(value = "isRead", required = false) Boolean isRead,
                                                        @RequestParam(value = "notificationType", required = false) NotificationType notificationType,
                                                        @RequestParam("page") int page,
                                                        @RequestParam("size") int size);

    // MANAGER 이상만 가능 - AI가 X-User-Id로 다시 검증하므로 여기선 groupId만 넘긴다
    @PostMapping("/api/v1/dashboard-notifications/read-all")
    void markAllAsRead(@RequestParam("groupId") Long groupId);
}
