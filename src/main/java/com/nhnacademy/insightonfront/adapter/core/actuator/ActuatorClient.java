package com.nhnacademy.insightonfront.adapter.core.actuator;

import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorNameUpdateRequest;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorRequest;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorResponse;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorRunLogResponse;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Core의 퍼블릭 액추에이터 API를 Gateway 경유로 호출한다.
 * <p><b>알려진 제약</b>: 다른 클라이언트들과 동일 — 로그인/세션 붙을 때 X-USER-ID 대신 Authorization
 * 헤더 전달로 바꿔야 실제로 끝까지 동작한다.
 */
@FeignClient(name = "insighton-gateway", contextId = "actuatorClient", url = "${service-url.gateway}")
public interface ActuatorClient {

    @PostMapping("/api/v1/groups/{group-id}/actuators")
    Long createActuator(@RequestHeader("X-USER-ID") Long userId,
                        @PathVariable("group-id") Long groupId,
                        @RequestBody ActuatorRequest request);

    @GetMapping("/api/v1/groups/{group-id}/actuators/{actuator-id}")
    ActuatorResponse getActuatorById(@RequestHeader("X-USER-ID") Long userId,
                                     @PathVariable("group-id") Long groupId,
                                     @PathVariable("actuator-id") Long actuatorId);

    @GetMapping("/api/v1/groups/{group-id}/actuators/location/{location-id}")
    List<ActuatorResponse> getActuatorsByLocationId(@RequestHeader("X-USER-ID") Long userId,
                                                     @PathVariable("group-id") Long groupId,
                                                     @PathVariable("location-id") Long locationId);

    @PutMapping("/api/v1/groups/{group-id}/actuators/{actuator-id}/state")
    void updateActuatorState(@RequestHeader("X-USER-ID") Long userId,
                             @PathVariable("group-id") Long groupId,
                             @PathVariable("actuator-id") Long actuatorId,
                             @RequestBody Map<String, Object> newState);

    @GetMapping("/api/v1/groups/{group-id}/actuators/{actuator-id}/logs")
    PageResponse<ActuatorRunLogResponse> getActuatorRunLogs(@RequestHeader("X-USER-ID") Long userId,
                                                              @PathVariable("group-id") Long groupId,
                                                              @PathVariable("actuator-id") Long actuatorId,
                                                              @RequestParam("page") int page,
                                                              @RequestParam("size") int size);

    @PutMapping("/api/v1/groups/{group-id}/actuators/{actuator-id}/name")
    void updateActuatorName(@RequestHeader("X-USER-ID") Long userId,
                            @PathVariable("group-id") Long groupId,
                            @PathVariable("actuator-id") Long actuatorId,
                            @RequestBody ActuatorNameUpdateRequest request);

    @DeleteMapping("/api/v1/groups/{group-id}/actuators/{actuator-id}")
    void deleteActuatorById(@RequestHeader("X-USER-ID") Long userId,
                            @PathVariable("group-id") Long groupId,
                            @PathVariable("actuator-id") Long actuatorId);

    @DeleteMapping("/api/v1/groups/{group-id}/actuators")
    void deleteAll(@RequestHeader("X-USER-ID") Long userId,
                   @PathVariable("group-id") Long groupId);
}
