package com.nhnacademy.insightonfront.domain.report.service;

import com.nhnacademy.insightonfront.adapter.ai.report.ReportClient;
import com.nhnacademy.insightonfront.adapter.ai.report.dto.ReportDetailResponse;
import com.nhnacademy.insightonfront.adapter.ai.report.dto.ReportListResponse;
import com.nhnacademy.insightonfront.adapter.ai.report.dto.ReportType;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.common.resolver.LocationNameResolver;
import com.nhnacademy.insightonfront.domain.report.dto.ReportDetailViewModel;
import com.nhnacademy.insightonfront.domain.report.dto.ReportListViewModel;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ReportClient(AI) 응답에 LocationNameResolver로 위치 이름을 붙여 화면용 뷰 모델로 조립
 */
@Service
@RequiredArgsConstructor
public class ReportViewService {

    private static final String UNKNOWN_LOCATION = "알 수 없는 위치";

    private final ReportClient reportClient;
    private final LocationNameResolver locationNameResolver;

    public PageResponse<ReportListViewModel> getReports(Long groupId, Long locationId, ReportType reportType,
                                                          OffsetDateTime from, OffsetDateTime to,
                                                          int page, int size, Long userId) {
        PageResponse<ReportListResponse> reports =
                reportClient.getReports(groupId, locationId, reportType, from, to, page, size, userId);
        Map<Long, String> locationNames = locationNameResolver.resolve(groupId, userId);
        return reports.map(r -> new ReportListViewModel(
                r.reportId(),
                locationNames.getOrDefault(r.locationId(), UNKNOWN_LOCATION),
                r.title(),
                r.reportType(),
                r.createdAt()
        ));
    }

    public ReportDetailViewModel getReport(Long reportId, Long userId) {
        ReportDetailResponse report = reportClient.getReport(reportId, userId);
        Map<Long, String> locationNames = locationNameResolver.resolve(report.groupId(), userId);
        return new ReportDetailViewModel(
                report.reportId(),
                locationNames.getOrDefault(report.locationId(), UNKNOWN_LOCATION),
                report.title(),
                report.reportType(),
                report.content(),
                report.createdAt()
        );
    }
}
