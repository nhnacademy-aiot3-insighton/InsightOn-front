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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Core의 퍼블릭 센서 API를 Gateway 경유로 호출한다.
 * <p><b>알려진 제약</b>: 다른 클라이언트들과 동일 — 로그인/세션 붙을 때 X-USER-ID 대신 Authorization
 * 헤더 전달로 바꿔야 실제로 끝까지 동작한다.
 */
@FeignClient(name = "insighton-gateway", contextId = "sensorClient", url = "${service-url.gateway}")
public interface SensorClient {

    @GetMapping("/api/v1/sensor/{id}")
    SensorResponse getSensor(@RequestHeader("X-USER-ID") Long userId,
                             @PathVariable("id") Long sensorId);

    @GetMapping("/api/v1/sensor/search")
    List<SensorResponse> search(@RequestHeader("X-USER-ID") Long userId,
                                @RequestParam("groupId") Long groupId,
                                @RequestParam(value = "id", required = false) Long id,
                                @RequestParam(value = "eui", required = false) String eui,
                                @RequestParam(value = "locationId", required = false) Long locationId,
                                @RequestParam(value = "sensorName", required = false) String sensorName);

    @PutMapping("/api/v1/sensor/{id}")
    void updateSensor(@RequestHeader("X-USER-ID") Long userId,
                      @PathVariable("id") Long id,
                      @RequestBody SensorUpdateRequest request);

    @DeleteMapping("/api/v1/sensor/{id}")
    void deleteSensor(@RequestHeader("X-USER-ID") Long userId,
                      @PathVariable("id") Long id);

    @DeleteMapping("/api/v1/sensor")
    void deleteAllSensor(@RequestHeader("X-USER-ID") Long userId,
                         @RequestParam("groupId") Long groupId);
}
