package com.nhnacademy.insightonfront.adapter.core.gateway;

import com.nhnacademy.insightonfront.adapter.core.gateway.dto.GatewayCreateRequest;
import com.nhnacademy.insightonfront.adapter.core.gateway.dto.GatewayResponse;
import com.nhnacademy.insightonfront.adapter.core.gateway.dto.GatewayUpdateRequest;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
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
 * Core의 퍼블릭 IoT 게이트웨이(MQTT/Modbus 브로커에 연결된 물리 장치) API를 Gateway 경유로 호출한다.
 * 여기서 말하는 "게이트웨이"는 센서/액추에이터가 연결된 물리 장치를 가리키며, 이 요청을 라우팅하는
 * API Gateway({@code insighton-gateway} 서비스)와는 무관한 별개의 개념이다.
 * <p><b>알려진 제약</b>: 다른 클라이언트들과 동일 — 로그인/세션 붙을 때 X-USER-ID 대신 Authorization
 * 헤더 전달로 바꿔야 실제로 끝까지 동작한다.
 */
@FeignClient(name = "insighton-gateway", contextId = "deviceGatewayClient")
public interface GatewayClient {

    @PostMapping("/api/v1/gateways")
    GatewayResponse create(@RequestHeader("X-User-Id") Long userId,
                           @RequestBody GatewayCreateRequest request);

    @GetMapping("/api/v1/gateways/{gatewayId}")
    GatewayResponse getById(@RequestHeader("X-User-Id") Long userId,
                            @PathVariable("gatewayId") Long gatewayId);

    @GetMapping("/api/v1/gateways")
    GatewayResponse getByGroupId(@RequestHeader("X-User-Id") Long userId,
                                 @RequestParam("groupId") Long groupId);

    @GetMapping("/api/v1/gateways/admin")
    PageResponse<GatewayResponse> getAll(@RequestHeader("X-User-Role") String userRole,
                                          @RequestParam("page") int page,
                                          @RequestParam("size") int size);

    @PutMapping("/api/v1/gateways/{gatewayId}")
    void update(@RequestHeader("X-User-Id") Long userId,
               @PathVariable("gatewayId") Long gatewayId,
               @RequestBody GatewayUpdateRequest request);

    @DeleteMapping("/api/v1/gateways/{gatewayId}")
    void delete(@RequestHeader("X-User-Id") Long userId,
               @PathVariable("gatewayId") Long gatewayId);
}
