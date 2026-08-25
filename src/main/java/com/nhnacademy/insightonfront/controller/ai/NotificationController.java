package com.nhnacademy.insightonfront.controller.ai;

import com.nhnacademy.insightonfront.adapter.ai.notification.dto.NotificationType;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.domain.notification.dto.DashboardNotificationViewModel;
import com.nhnacademy.insightonfront.domain.notification.service.DashboardNotificationViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * "전체 알림 보기" 페이지. 헤더 벨(/groups/notifications, 안읽음만·JSON)과 달리
 * 여기는 서버 렌더링 목록 + 페이징이고, 읽음여부/타입 필터를 지원한다.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/my-group/notifications")
public class NotificationController {

    private final DashboardNotificationViewService dashboardNotificationViewService;

    @GetMapping
    public String list(@CookieValue(value = "userId", required = false) Long userId,
                        @CookieValue(value = "groupId", required = false) Long groupId,
                        @RequestParam(required = false) Boolean isRead,
                        @RequestParam(required = false) NotificationType notificationType,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        PageResponse<DashboardNotificationViewModel> notifications =
                dashboardNotificationViewService.searchNotifications(groupId, isRead, notificationType, page, size);

        model.addAttribute("notifications", notifications);
        model.addAttribute("selectedIsRead", isRead);
        model.addAttribute("selectedNotificationType", notificationType);
        return "notification/list";
    }
}
