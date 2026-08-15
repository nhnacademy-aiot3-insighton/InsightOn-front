package com.nhnacademy.insightonfront.adapter.core.location;

import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationCreateRequest;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationDetailResponse;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationListResponse;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationUpdateRequest;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Core의 퍼블릭 위치 API를 Gateway 경유로 호출한다
 * <p><b>알려진 제약</b>: 다른 클라이언트들과 동일 — 로그인/세션 붙을 때 X-USER-ID 대신 Authorization
 * 헤더 전달로 바꿔야 실제로 끝까지 동작한다.
 */
@FeignClient(name = "insighton-gateway", contextId = "locationClient", url = "${service-url.gateway}")
public interface LocationClient {

    @PostMapping("/api/v1/groups/{group-id}/location/create")
    void createLocation(@PathVariable("group-id") Long groupId,
                        @RequestBody LocationCreateRequest request,
                        @RequestHeader("X-USER-ID") Long userId);

    @GetMapping("/api/v1/groups/{group-id}/location/list")
    List<LocationListResponse> getLocationList(@PathVariable("group-id") Long groupId,
                                               @RequestHeader("X-USER-ID") Long userId);

    @GetMapping("/api/v1/groups/{group-id}/location/{location-id}")
    LocationDetailResponse getLocation(@PathVariable("group-id") Long groupId,
                                       @PathVariable("location-id") Long locationId,
                                       @RequestHeader("X-USER-ID") Long userId);

    @PutMapping("/api/v1/groups/{group-id}/location/{location-id}/toggle-mode")
    void toggleAutoControlMode(@PathVariable("group-id") Long groupId,
                               @PathVariable("location-id") Long locationId,
                               @RequestHeader("X-USER-ID") Long userId);

    @PutMapping("/api/v1/groups/{group-id}/location/{location-id}/update")
    void updateName(@PathVariable("group-id") Long groupId,
                    @PathVariable("location-id") Long locationId,
                    @RequestBody LocationUpdateRequest request,
                    @RequestHeader("X-USER-ID") Long userId);

    @DeleteMapping("/api/v1/groups/{group-id}/location/{location-id}/delete")
    void deleteLocation(@PathVariable("group-id") Long groupId,
                        @PathVariable("location-id") Long locationId,
                        @RequestHeader("X-USER-ID") Long userId);
}
