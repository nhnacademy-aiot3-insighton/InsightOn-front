package com.nhnacademy.insightonfront.domain.notification.service;

import com.nhnacademy.insightonfront.adapter.ai.notification.DashboardNotificationClient;
import com.nhnacademy.insightonfront.adapter.ai.notification.dto.DashboardNotificationResponse;
import com.nhnacademy.insightonfront.common.resolver.LocationNameResolver;
import com.nhnacademy.insightonfront.domain.notification.dto.DashboardNotificationViewModel;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * DashboardNotificationClient(AI) 응답에 LocationNameResolver로 위치 이름을 붙여 화면용 뷰 모델로 조립
 */
@Service
@RequiredArgsConstructor
public class DashboardNotificationViewService {

    private static final String UNKNOWN_LOCATION = "알 수 없는 위치";

    private final DashboardNotificationClient dashboardNotificationClient;
    private final LocationNameResolver locationNameResolver;

    public List<DashboardNotificationViewModel> getUnreadNotifications(Long groupId) {
        List<DashboardNotificationResponse> notifications =
                dashboardNotificationClient.getUnreadNotifications(groupId);
        Map<Long, String> locationNames = locationNameResolver.resolve(groupId);
        return notifications.stream()
                .map(n -> toViewModel(n, locationNames))
                .toList();
    }

    public DashboardNotificationViewModel markAsRead(Long dashboardNotificationId, Long groupId) {
        DashboardNotificationResponse notification =
                dashboardNotificationClient.markAsRead(dashboardNotificationId);
        Map<Long, String> locationNames = locationNameResolver.resolve(groupId);
        return toViewModel(notification, locationNames);
    }

    private DashboardNotificationViewModel toViewModel(DashboardNotificationResponse notification,
                                                         Map<Long, String> locationNames) {
        return new DashboardNotificationViewModel(
                notification.dashboardNotificationId(),
                locationNames.getOrDefault(notification.locationId(), UNKNOWN_LOCATION),
                notification.notificationType(),
                notification.sourceId(),
                notification.title(),
                notification.isRead(),
                notification.createdAt()
        );
    }
}
