package com.nhnacademy.insightonfront.dto.dashboard;


import com.nhnacademy.insightonfront.dto.widget.WidgetsListResponse;
import lombok.Builder;

import java.util.List;

/**
 * dashboard 조회 시 응답 DTO
 *
 * @param dashboardId 조회 할 dashboard ID
 * @param widgetsList dashboard에 존재하는 widget List
 * @param title       dashboard 제목(?)
 */
@Builder
public record DashboardResponse(
        Long dashboardId,
        List<WidgetsListResponse> widgetsList,
        String title) {
}
