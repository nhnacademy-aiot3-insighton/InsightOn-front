package com.nhnacademy.insightonfront.dto.widget;

import lombok.Builder;

/**
 * 위젯 조회 리스트 응답 DTO
 *
 * @param widgetId     조회하려는 widget ID
 * @param dashboardId  widget이 속해있는 dashboard ID
 * @param xPos         그리드 레이아웃 상에서의 가로 시작 그리드 번호 위치 (X 좌표)
 * @param yPos         그리드 레이아웃 상에서의 세로 시작 그리드 번호 위치 (Y 좌표)
 * @param width        위젯 컴포넌트가 가로로 차지하는 격자 폭 셀(Cell) 크기
 * @param height       위젯 컴포넌트가 세로로 차지하는 격자 높이 셀(Cell) 크기
 * @param widgetConfig InfluxDB 연동 및 데이터 조회를 위한 상세 설정 객체
 */
@Builder
public record WidgetsListResponse(
        Long widgetId,
        Long dashboardId,
        int xPos,
        int yPos,
        int width,
        int height,
        WidgetConfig widgetConfig
) {
}
