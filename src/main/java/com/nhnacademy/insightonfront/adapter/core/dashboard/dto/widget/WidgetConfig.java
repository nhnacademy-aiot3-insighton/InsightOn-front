package com.nhnacademy.insightonfront.adapter.core.dashboard.dto.widget;

import lombok.Builder;

import java.util.List;

/**
 * InfluxDB 시계열 데이터 조회를 위한 위젯 설정(JSONB) DTO
 * <p>
 * bucket          InfluxDB 데이터가 저장된 버킷 이름 - insighton-bucket
 * measurement     InfluxDB 측정 항목/테이블 이름 - sensor_data
 *
 * @param sensorEui       조회 타겟 센서 센서 식별자 (ex. DEV_LOCATION_01)
 * @param range           조회할 과거 시간 범위 (ex. -1h, -24h, -7d)
 * @param aggregateWindow 시계열 데이터 집계/다운샘플링 주기 (ex. 1m, 15m, 1h)
 * @param fields          조회하여 차트에 표시할 메트릭/필드명 리스트 (ex. ["temperature", "humidity"])
 */
@Builder
public record WidgetConfig(
        Type type,
        String sensorEui,
        String range,
        String aggregateWindow,
        List<String> fields, // 목록을 띄워야하나? (front에 사용자가 볼 수 있도록)

        // front에서 값 받아오도록,,,
        Long groupId,
        Long locationId
) {
    public enum Type {
        GAUGE, GRAPH, SINGLE_STAT
    }
}
