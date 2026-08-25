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

/**
 * Core의 퍼블릭 위치 API를 Gateway 경유로 호출한다.
 * userId는 안 넘긴다 — 게이트웨이가 Authorization을 검증해서 X-User-Id로 바꿔 Core에 넘겨준다.
 */
@FeignClient(name = "insighton-gateway", contextId = "locationClient", url = "${service-url.gateway}")
public interface LocationClient {

    @PostMapping("/api/v1/groups/{group-id}/location/create")
    void createLocation(@PathVariable("group-id") Long groupId,
                        @RequestBody LocationCreateRequest request);

    @GetMapping("/api/v1/groups/{group-id}/location/list")
    List<LocationListResponse> getLocationList(@PathVariable("group-id") Long groupId);

    @GetMapping("/api/v1/groups/{group-id}/location/{location-id}")
    LocationDetailResponse getLocation(@PathVariable("group-id") Long groupId,
                                       @PathVariable("location-id") Long locationId);

    @PutMapping("/api/v1/groups/{group-id}/location/{location-id}/toggle-mode")
    void toggleAutoControlMode(@PathVariable("group-id") Long groupId,
                               @PathVariable("location-id") Long locationId);

    @PutMapping("/api/v1/groups/{group-id}/location/{location-id}/update")
    void updateName(@PathVariable("group-id") Long groupId,
                    @PathVariable("location-id") Long locationId,
                    @RequestBody LocationUpdateRequest request);

    @DeleteMapping("/api/v1/groups/{group-id}/location/{location-id}/delete")
    void deleteLocation(@PathVariable("group-id") Long groupId,
                        @PathVariable("location-id") Long locationId);
}
