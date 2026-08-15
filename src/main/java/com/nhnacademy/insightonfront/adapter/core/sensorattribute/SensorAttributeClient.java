package com.nhnacademy.insightonfront.adapter.core.sensorattribute;

import com.nhnacademy.insightonfront.adapter.core.sensorattribute.dto.SensorAttributeResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Core의 퍼블릭 센서 속성 API를 Gateway 경유로 호출한다.
 * <p><b>알려진 제약</b>: 다른 클라이언트들과 동일 — 로그인/세션 붙을 때 X-USER-ID 대신 Authorization
 * 헤더 전달로 바꿔야 실제로 끝까지 동작한다.
 */
@FeignClient(name = "insighton-gateway", contextId = "sensorAttributeClient", url = "${service-url.gateway}")
public interface SensorAttributeClient {

    @GetMapping("/api/v1/sensor/{sensor-id}/attribute")
    List<SensorAttributeResponse> getSensorAttribute(@RequestHeader("X-USER-ID") Long userId,
                                                       @PathVariable("sensor-id") Long sensorId);

    @DeleteMapping("/api/v1/sensor/{sensor-id}/attribute/{metric-key}")
    void deleteSensorAttribute(@RequestHeader("X-USER-ID") Long userId,
                               @PathVariable("sensor-id") Long sensorId,
                               @PathVariable("metric-key") String metricKey);
}
