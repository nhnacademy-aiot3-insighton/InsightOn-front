package com.nhnacademy.insightonfront.domain.telemetrystats.service;

import com.nhnacademy.insightonfront.adapter.ai.telemetrystats.HourlyTelemetryStatClient;
import com.nhnacademy.insightonfront.adapter.ai.telemetrystats.dto.HourlyTelemetryStatResponse;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.common.resolver.LocationNameResolver;
import com.nhnacademy.insightonfront.domain.telemetrystats.dto.HourlyTelemetryStatViewModel;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * HourlyTelemetryStatClient(AI) 응답에 LocationNameResolver로 위치 이름을 붙여 화면용 뷰 모델로 조립한다.
 */
@Service
@RequiredArgsConstructor
public class HourlyTelemetryStatViewService {

    private static final String UNKNOWN_LOCATION = "알 수 없는 위치";

    private final HourlyTelemetryStatClient hourlyTelemetryStatClient;
    private final LocationNameResolver locationNameResolver;

    public PageResponse<HourlyTelemetryStatViewModel> getHourlyTelemetryStats(Long groupId, Long locationId,
                                                                                OffsetDateTime from, OffsetDateTime to,
                                                                                int page, int size, Long userId) {
        PageResponse<HourlyTelemetryStatResponse> stats =
                hourlyTelemetryStatClient.getHourlyTelemetryStats(groupId, locationId, from, to, page, size, userId);
        Map<Long, String> locationNames = locationNameResolver.resolve(groupId, userId);
        return stats.map(s -> new HourlyTelemetryStatViewModel(
                s.hourlyTelemetryStatId(),
                locationNames.getOrDefault(s.locationId(), UNKNOWN_LOCATION),
                s.logHour(),
                s.metricsAvg(),
                s.metricsMax(),
                s.metricsMin(),
                s.actuatorOnMinutes(),
                s.createdAt()
        ));
    }
}
