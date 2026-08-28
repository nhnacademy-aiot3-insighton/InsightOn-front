package com.nhnacademy.insightonfront.adapter.core.groupregistration;

import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.CreateGroupRegistrationRequest;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationResponse;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationStatus;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * Core의 퍼블릭 그룹 등록 신청 API를 Gateway 경유로 호출한다.
 * userId는 안 넘긴다 — 게이트웨이가 Authorization을 검증해서 X-User-Id로 바꿔 Core에 넘겨준다.
 */
@FeignClient(name = "insighton-gateway", contextId = "groupRegistrationClient", url = "${service-url.gateway}")
public interface GroupRegistrationClient {

    @PostMapping("/api/v1/group-registrations")
    GroupRegistrationResponse createRequest(@RequestBody CreateGroupRegistrationRequest request);

    @GetMapping("/api/v1/group-registrations")
    PageResponse<GroupRegistrationResponse> getGroupRegistrations(@RequestParam(value = "status", required = false) GroupRegistrationStatus status,
                                                                  @RequestParam("page") int page,
                                                                  @RequestParam("size") int size,
                                                                  @RequestParam("sort") String sort);

    @GetMapping("/api/v1/group-registrations/my")
    PageResponse<GroupRegistrationResponse> getMyGroupRegistrations(@RequestParam("page") int page,
                                                                    @RequestParam("size") int size,
                                                                    @RequestParam("sort") String sort);

    @GetMapping("/api/v1/group-registrations/{group-registration-id}")
    GroupRegistrationResponse getGroupRegistration(@PathVariable("group-registration-id") Long groupRegistrationId);

    @PutMapping("/api/v1/group-registrations/{group-registration-id}/cancel")
    void cancelGroupRegistration(@PathVariable("group-registration-id") Long groupRegistrationId);

    @PutMapping("/api/v1/group-registrations/{group-registration-id}/approve")
    void approveGroupRegistration(@PathVariable("group-registration-id") Long groupRegistrationId);

    @PutMapping("/api/v1/group-registrations/{group-registration-id}/reject")
    void rejectGroupRegistration(@PathVariable("group-registration-id") Long groupRegistrationId);
}
