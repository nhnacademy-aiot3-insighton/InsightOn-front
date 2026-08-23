package com.nhnacademy.insightonfront.adapter.core.sensor;

import com.nhnacademy.insightonfront.adapter.core.sensor.dto.SensorResponse;
import com.nhnacademy.insightonfront.adapter.core.sensor.dto.SensorUpdateRequest;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Core의 퍼블릭 센서 API를 Gateway 경유로 호출한다.
 * userId는 안 넘긴다 — 게이트웨이가 Authorization을 검증해서 X-User-Id로 바꿔 Core에 넘겨준다.
 */
@FeignClient(name = "insighton-gateway", contextId = "sensorClient", url = "${service-url.gateway}")
public interface SensorClient {

    @GetMapping("/api/v1/sensor/{id}")
    SensorResponse getSensor(@PathVariable("id") Long sensorId);

    @GetMapping("/api/v1/sensor/search")
    List<SensorResponse> search(@RequestParam("groupId") Long groupId,
                                @RequestParam(value = "id", required = false) Long id,
                                @RequestParam(value = "eui", required = false) String eui,
                                @RequestParam(value = "locationId", required = false) Long locationId,
                                @RequestParam(value = "sensorName", required = false) String sensorName);

    @PutMapping("/api/v1/sensor/{id}")
    void updateSensor(@PathVariable("id") Long id,
                      @RequestBody SensorUpdateRequest request);

    @DeleteMapping("/api/v1/sensor/{id}")
    void deleteSensor(@PathVariable("id") Long id);

    @DeleteMapping("/api/v1/sensor")
    void deleteAllSensor(@RequestParam("groupId") Long groupId);
}
