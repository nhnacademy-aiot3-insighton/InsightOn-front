package com.nhnacademy.insightonfront.controller.ai;

import com.nhnacademy.insightonfront.domain.notification.dto.DashboardNotificationViewModel;
import com.nhnacademy.insightonfront.domain.notification.service.DashboardNotificationViewService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

// 다른 AI 컨트롤러와 달리 화면 새로고침 없이 헤더 알림 벨에서 바로 쓰는 용도라 JSON으로 응답한다(@RestController).
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/groups/notifications")
public class DashboardNotificationController {

    private final DashboardNotificationViewService dashboardNotificationViewService;

    @GetMapping
    public List<DashboardNotificationViewModel> getUnreadNotifications(@CookieValue("groupId") Long groupId) {
        return dashboardNotificationViewService.getUnreadNotifications(groupId);
    }

    @PostMapping("/{dashboard-notification-id}/read")
    public DashboardNotificationViewModel markAsRead(@CookieValue("groupId") Long groupId,
                                                      @PathVariable("dashboard-notification-id")
                                                      Long dashboardNotificationId) {
        return dashboardNotificationViewService.markAsRead(dashboardNotificationId, groupId);
    }
}
