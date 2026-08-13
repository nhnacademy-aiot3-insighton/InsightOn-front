package com.nhnacademy.insightonfront.adapter.core.dashboard.dto.widget;


import lombok.Builder;

/**
 * 한번에 저장할 때 받아올 dto
 *
 * @param widgetId     생성되는 widget이라면 null 값으로 들어오고 수정이라면 값이 들어옴
 * @param xPos         그리드 레이아웃 상에서의 가로 시작 그리드 번호 위치 (X 좌표)
 * @param yPos         그리드 레이아웃 상에서의 세로 시작 그리드 번호 위치 (Y 좌표)
 * @param width        위젯 컴포넌트가 가로로 차지하는 격자 폭 셀(Cell) 크기
 * @param height       위젯 컴포넌트가 세로로 차지하는 격자 높이 셀(Cell) 크기
 * @param widgetConfig influxDB에 query를 날리기 위한 값들
 */
@Builder
public record WidgetSaveRequest(
        Long widgetId, // << 이거 null이면 생성 요청, null이 아니라면 수정 요청
        Integer xPos,
        Integer yPos,
        Integer width,
        Integer height,
        WidgetConfig widgetConfig

) {
}
