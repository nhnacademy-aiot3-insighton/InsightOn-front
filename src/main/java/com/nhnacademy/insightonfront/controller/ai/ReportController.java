package com.nhnacademy.insightonfront.controller.ai;

import com.nhnacademy.insightonfront.adapter.ai.report.dto.ReportType;
import com.nhnacademy.insightonfront.adapter.core.location.LocationClient;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationListResponse;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.domain.report.dto.ReportDetailViewModel;
import com.nhnacademy.insightonfront.domain.report.dto.ReportListViewModel;
import com.nhnacademy.insightonfront.domain.report.service.ReportViewService;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.CookieValue;

/**
 * AI가 생성한 리포트(주간/월간)를 그룹 안에서 조회한다. groupId는 쿠키에서 읽는다
 * — 한 유저는 그룹 하나에만 속해서 groupId를 URL에 실을 필요가 없다.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/my-group/reports")
public class ReportController {

    private final ReportViewService reportViewService;
    private final LocationClient locationClient;

    @GetMapping
    public String list(@CookieValue(value = "userId", required = false) Long userId,
                        @CookieValue(value = "groupId", required = false) Long groupId,
                        @RequestParam(required = false) Long locationId,
                        @RequestParam(required = false) ReportType reportType,
                        @RequestParam(required = false) OffsetDateTime from,
                        @RequestParam(required = false) OffsetDateTime to,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        PageResponse<ReportListViewModel> reports =
                reportViewService.getReports(groupId, locationId, reportType, from, to, page, size);
        List<LocationListResponse> locations = locationClient.getLocationList(groupId);

        model.addAttribute("reports", reports);
        model.addAttribute("locations", locations);
        model.addAttribute("selectedLocationId", locationId);
        model.addAttribute("selectedReportType", reportType);
        return "report/list";
    }

    @GetMapping("/{report-id}")
    public String detail(@CookieValue(value = "userId", required = false) Long userId,
                          @CookieValue(value = "groupId", required = false) Long groupId,
                          @PathVariable("report-id") Long reportId,
                          Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        ReportDetailViewModel report = reportViewService.getReport(reportId);
        model.addAttribute("report", report);
        return "report/detail";
    }
}
