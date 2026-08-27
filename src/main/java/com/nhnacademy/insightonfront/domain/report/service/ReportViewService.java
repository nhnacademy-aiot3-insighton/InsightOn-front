package com.nhnacademy.insightonfront.domain.report.service;

import com.nhnacademy.insightonfront.adapter.ai.report.ReportClient;
import com.nhnacademy.insightonfront.adapter.ai.report.dto.ReportDetailResponse;
import com.nhnacademy.insightonfront.adapter.ai.report.dto.ReportListResponse;
import com.nhnacademy.insightonfront.adapter.ai.report.dto.ReportType;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.common.resolver.LocationNameResolver;
import com.nhnacademy.insightonfront.domain.report.dto.ReportDetailViewModel;
import com.nhnacademy.insightonfront.domain.report.dto.ReportListViewModel;
import com.nhnacademy.insightonfront.domain.report.dto.ReportTelemetryHighlightViewModel;
import com.nhnacademy.insightonfront.domain.telemetrystats.dto.HourlyTelemetryStatViewModel;
import com.nhnacademy.insightonfront.domain.telemetrystats.service.HourlyTelemetryStatViewService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ReportClient(AI) 응답에 LocationNameResolver로 위치 이름을 붙여 화면용 뷰 모델로 조립
 */
@Service
@RequiredArgsConstructor
public class ReportViewService {

    private static final String UNKNOWN_LOCATION = "알 수 없는 위치";
    // AI가 리포트 프롬프트를 만들 때 쓰는 습도 쾌적 기준 상한(ReportGenerationScheduler.COMFORT_RANGE)과 동일하게 맞춤
    private static final double HUMIDITY_COMFORT_MAX = 60.0;
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final ReportClient reportClient;
    private final LocationNameResolver locationNameResolver;
    private final HourlyTelemetryStatViewService hourlyTelemetryStatViewService;
    private final ObjectMapper objectMapper;

    public PageResponse<ReportListViewModel> getReports(Long groupId, Long locationId, ReportType reportType,
                                                          OffsetDateTime from, OffsetDateTime to,
                                                          int page, int size) {
        PageResponse<ReportListResponse> reports =
                reportClient.getReports(groupId, locationId, reportType, from, to, page, size);
        Map<Long, String> locationNames = locationNameResolver.resolve(groupId);
        return reports.map(r -> new ReportListViewModel(
                r.reportId(),
                locationNames.getOrDefault(r.locationId(), UNKNOWN_LOCATION),
                r.title(),
                r.reportType(),
                r.createdAt()
        ));
    }

    public ReportDetailViewModel getReport(Long reportId) {
        ReportDetailResponse report = reportClient.getReport(reportId);
        Map<Long, String> locationNames = locationNameResolver.resolve(report.groupId());

        // AI 배치가 리포트를 만들 당시 집계 기간(ReportGenerationScheduler)을 createdAt으로 역산함 —
        // 리포트 자체엔 기간이 저장 안 돼 있어서, 생성 당일 자정 기준으로 주간은 1주 전~1시간 전,
        // 월간은 1달 전~1시간 전으로 계산(배치가 실제로 쓰는 값과 동일한 로직)
        OffsetDateTime generationDay = report.createdAt().truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime periodEnd = generationDay.minusHours(1);
        OffsetDateTime periodStart = report.reportType() == ReportType.WEEKLY
                ? generationDay.minusWeeks(1)
                : generationDay.minusMonths(1);

        ReportTelemetryHighlightViewModel highlight =
                computeHighlight(report.groupId(), report.locationId(), periodStart, periodEnd);

        return new ReportDetailViewModel(
                report.reportId(),
                report.locationId(),
                locationNames.getOrDefault(report.locationId(), UNKNOWN_LOCATION),
                report.title(),
                report.reportType(),
                report.content(),
                report.createdAt(),
                periodStart,
                periodEnd,
                highlight
        );
    }

    /**
     * 이 기간의 시간별 원자료를 그래프 대신 문장으로 읽을 수 있게 요약함 — 최고 기온이 찍힌 시각
     * 하나, 습도가 쾌적 기준을 처음 넘긴 시각 하나만 뽑고, 액추에이터 가동시간은 기간 전체 합
     */
    private ReportTelemetryHighlightViewModel computeHighlight(Long groupId, Long locationId,
                                                                OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        PageResponse<HourlyTelemetryStatViewModel> stats =
                hourlyTelemetryStatViewService.getHourlyTelemetryStats(groupId, locationId, periodStart, periodEnd, 0, 2000);

        Double peakTemperature = null;
        OffsetDateTime peakTemperatureHour = null;
        OffsetDateTime firstPoorHumidityHour = null;
        double aircondMinutes = 0;
        double airPurifierMinutes = 0;

        for (HourlyTelemetryStatViewModel row : stats.content()) {
            JsonNode avg = parseJson(row.metricsAvg());
            JsonNode actuator = parseJson(row.actuatorOnMinutes());
            OffsetDateTime hourInKst = row.logHour().withOffsetSameInstant(KST);

            JsonNode temperatureNode = avg.path("temperature");
            if (!temperatureNode.isMissingNode()) {
                double temperature = temperatureNode.asDouble();
                if (peakTemperature == null || temperature > peakTemperature) {
                    peakTemperature = temperature;
                    peakTemperatureHour = hourInKst;
                }
            }

            JsonNode humidityNode = avg.path("humidity");
            if (!humidityNode.isMissingNode() && humidityNode.asDouble() > HUMIDITY_COMFORT_MAX
                    && firstPoorHumidityHour == null) {
                firstPoorHumidityHour = hourInKst;
            }

            // 가끔 음수로 집계되는 경우가 있어(원본 데이터 이슈), 시간 합산에 음수가 섞여
            // 총 가동시간이 줄어드는 걸 막으려고 시간당 0 미만은 0으로 취급함
            aircondMinutes += Math.max(0.0, actuator.path("AIRCON").asDouble(0.0));
            airPurifierMinutes += Math.max(0.0, actuator.path("AIR_PURIFIER").asDouble(0.0));
        }

        return new ReportTelemetryHighlightViewModel(
                !stats.content().isEmpty(),
                peakTemperature,
                peakTemperatureHour,
                roundToOneDecimalHours(aircondMinutes),
                firstPoorHumidityHour != null,
                firstPoorHumidityHour,
                roundToOneDecimalHours(airPurifierMinutes)
        );
    }

    private double roundToOneDecimalHours(double minutes) {
        return Math.round(minutes / 60.0 * 10) / 10.0;
    }

    private JsonNode parseJson(String json) {
        if (json == null) {
            return objectMapper.missingNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.missingNode();
        }
    }
}
