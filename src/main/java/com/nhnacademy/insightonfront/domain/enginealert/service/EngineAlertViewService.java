package com.nhnacademy.insightonfront.domain.enginealert.service;

import com.nhnacademy.insightonfront.adapter.ai.enginealert.EngineAlertClient;
import com.nhnacademy.insightonfront.adapter.ai.enginealert.dto.EngineAlertResponse;
import com.nhnacademy.insightonfront.adapter.ai.enginealert.dto.Severity;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.common.resolver.LocationNameResolver;
import com.nhnacademy.insightonfront.domain.enginealert.dto.EngineAlertViewModel;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * EngineAlertClient(AI) 응답에 LocationNameResolver로 위치 이름을 붙여 화면용 뷰 모델로 조립
 */
@Service
@RequiredArgsConstructor
public class EngineAlertViewService {

    private static final String UNKNOWN_LOCATION = "알 수 없는 위치";

    private final EngineAlertClient engineAlertClient;
    private final LocationNameResolver locationNameResolver;

    public PageResponse<EngineAlertViewModel> getEngineAlerts(Long groupId, Long locationId, Severity severity,
                                                                OffsetDateTime from, OffsetDateTime to,
                                                                int page, int size) {
        PageResponse<EngineAlertResponse> alerts =
                engineAlertClient.getEngineAlerts(groupId, locationId, severity, from, to, page, size);
        Map<Long, String> locationNames = locationNameResolver.resolve(groupId);
        return alerts.map(a -> toViewModel(a, locationNames));
    }

    public EngineAlertViewModel getEngineAlert(Long engineAlertId) {
        EngineAlertResponse alert = engineAlertClient.getEngineAlert(engineAlertId);
        Map<Long, String> locationNames = locationNameResolver.resolve(alert.groupId());
        return toViewModel(alert, locationNames);
    }

    private EngineAlertViewModel toViewModel(EngineAlertResponse alert, Map<Long, String> locationNames) {
        return new EngineAlertViewModel(
                alert.engineAlertId(),
                locationNames.getOrDefault(alert.locationId(), UNKNOWN_LOCATION),
                alert.flowId(),
                alert.title(),
                alert.message(),
                alert.severity(),
                alert.triggerValue(),
                alert.createdAt()
        );
    }
}
