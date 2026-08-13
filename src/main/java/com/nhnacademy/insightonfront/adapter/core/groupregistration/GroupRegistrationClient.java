package com.nhnacademy.insightonfront.adapter.core.groupregistration;

import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.CreateGroupRegistrationRequest;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationResponse;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationStatus;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Core의 퍼블릭 그룹 등록 신청 API를 Gateway 경유로 호출한다.
 * <p><b>알려진 제약</b>: 다른 클라이언트들과 동일 — 로그인/세션 붙을 때 X-USER-ID 대신 Authorization
 * 헤더 전달로 바꿔야 실제로 끝까지 동작한다.
 */
@FeignClient(name = "insighton-gateway", contextId = "groupRegistrationClient")
public interface GroupRegistrationClient {

    @PostMapping("/api/v1/group-registrations")
    GroupRegistrationResponse createRequest(@RequestHeader("X-User-Id") Long requesterId,
                                            @RequestBody CreateGroupRegistrationRequest request);

    @GetMapping("/api/v1/group-registrations")
    PageResponse<GroupRegistrationResponse> getGroupRegistrations(@RequestHeader(value = "X-User-Role", required = false) String userRole,
                                                                    @RequestParam(value = "status", required = false) GroupRegistrationStatus status,
                                                                    @RequestParam("page") int page,
                                                                    @RequestParam("size") int size);

    @GetMapping("/api/v1/group-registrations/my")
    PageResponse<GroupRegistrationResponse> getMyGroupRegistrations(@RequestHeader("X-User-Id") Long requesterId,
                                                                      @RequestParam("page") int page,
                                                                      @RequestParam("size") int size);

    @GetMapping("/api/v1/group-registrations/{group-registration-id}")
    GroupRegistrationResponse getGroupRegistration(@RequestHeader("X-User-Id") Long userId,
                                                    @RequestHeader(value = "X-User-Role", required = false) String userRole,
                                                    @PathVariable("group-registration-id") Long groupRegistrationId);

    @PutMapping("/api/v1/group-registrations/{group-registration-id}/cancel")
    void cancelGroupRegistration(@RequestHeader("X-User-Id") Long requesterId,
                                 @PathVariable("group-registration-id") Long groupRegistrationId);

    @PutMapping("/api/v1/group-registrations/{group-registration-id}/approve")
    void approveGroupRegistration(@RequestHeader(value = "X-User-Role", required = false) String userRole,
                                  @RequestHeader("X-User-Id") Long approverId,
                                  @PathVariable("group-registration-id") Long groupRegistrationId);

    @PutMapping("/api/v1/group-registrations/{group-registration-id}/reject")
    void rejectGroupRegistration(@RequestHeader(value = "X-User-Role", required = false) String userRole,
                                 @RequestHeader("X-User-Id") Long approverId,
                                 @PathVariable("group-registration-id") Long groupRegistrationId);
}
