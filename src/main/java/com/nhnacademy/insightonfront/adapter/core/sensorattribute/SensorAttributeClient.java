package com.nhnacademy.insightonfront.adapter.core.sensorattribute;

import com.nhnacademy.insightonfront.adapter.core.sensorattribute.dto.SensorAttributeResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Core의 퍼블릭 센서 속성 API를 Gateway 경유로 호출한다.
 * userId는 안 넘긴다 — 게이트웨이가 Authorization을 검증해서 X-User-Id로 바꿔 Core에 넘겨준다.
 */
@FeignClient(name = "insighton-gateway", contextId = "sensorAttributeClient", url = "${service-url.gateway}")
public interface SensorAttributeClient {

    @GetMapping("/api/v1/sensor/{sensor-id}/attribute")
    List<SensorAttributeResponse> getSensorAttribute(@PathVariable("sensor-id") Long sensorId);

    @DeleteMapping("/api/v1/sensor/{sensor-id}/attribute/{metric-key}")
    void deleteSensorAttribute(@PathVariable("sensor-id") Long sensorId,
                               @PathVariable("metric-key") String metricKey);
}
