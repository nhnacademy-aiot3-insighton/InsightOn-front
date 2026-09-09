package com.nhnacademy.insightonfront.controller.ai;

import com.nhnacademy.insightonfront.domain.notification.dto.DashboardNotificationViewModel;
import com.nhnacademy.insightonfront.domain.notification.service.DashboardNotificationViewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 다른 AI 컨트롤러와 달리 화면 새로고침 없이 헤더 알림 벨에서 바로 쓰는 용도라 JSON으로 응답한다(@RestController).
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/groups/notifications")
public class DashboardNotificationController {

    private final DashboardNotificationViewService dashboardNotificationViewService;

    @GetMapping
    public List<DashboardNotificationViewModel> getUnreadNotifications(@CookieValue(value = "groupId", required = false) Long groupId) {
        if (groupId == null) {
            throw new IllegalArgumentException("소속된 그룹이 없습니다.");
        }
        return dashboardNotificationViewService.getUnreadNotifications(groupId);
    }

    @PostMapping("/{dashboard-notification-id}/read")
    public DashboardNotificationViewModel markAsRead(@CookieValue(value = "groupId", required = false) Long groupId,
                                                      @PathVariable("dashboard-notification-id")
                                                      Long dashboardNotificationId) {
        if (groupId == null) {
            throw new IllegalArgumentException("소속된 그룹이 없습니다.");
        }
        return dashboardNotificationViewService.markAsRead(dashboardNotificationId, groupId);
    }
}
