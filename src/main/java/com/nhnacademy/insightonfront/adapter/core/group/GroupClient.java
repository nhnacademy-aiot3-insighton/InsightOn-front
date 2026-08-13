package com.nhnacademy.insightonfront.adapter.core.group;

import com.nhnacademy.insightonfront.adapter.core.group.dto.GroupAdminResponse;
import com.nhnacademy.insightonfront.adapter.core.group.dto.GroupRequest;
import com.nhnacademy.insightonfront.adapter.core.group.dto.GroupResponse;
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
 * Core의 퍼블릭 그룹 API를 Gateway 경유로 호출한다.
 * <p><b>알려진 제약</b>: 다른 클라이언트들과 동일 — 로그인/세션 붙을 때 X-USER-ID 대신 Authorization
 * 헤더 전달로 바꿔야 실제로 끝까지 동작한다.
 */
@FeignClient(name = "insighton-gateway", contextId = "groupClient")
public interface GroupClient {

    @PostMapping("/api/v1/groups/create")
    void createGroup(@RequestHeader("X-USER-ID") Long userId,
                     @RequestBody GroupRequest request);

    @GetMapping("/api/v1/groups/{group-id}/my-group")
    GroupResponse getMyGroup(@RequestHeader("X-USER-ID") Long userId,
                             @PathVariable("group-id") Long groupId);

    @GetMapping("/api/v1/groups/{group-id}/preview")
    GroupResponse getGroupPreview(@RequestHeader("X-USER-ID") Long userId,
                                  @PathVariable("group-id") Long groupId,
                                  @RequestParam("inviteToken") String inviteToken);

    @GetMapping("/api/v1/groups/admin/group-list")
    PageResponse<GroupAdminResponse> getGroupList(@RequestHeader("X-USER-ROLE") String userRole,
                                                   @RequestHeader("X-USER-ID") Long userId,
                                                   @RequestParam("page") int page,
                                                   @RequestParam("size") int size);

    @PutMapping("/api/v1/groups/{group-id}/invite-token/new")
    void newInviteToken(@RequestHeader("X-USER-ID") Long userId,
                        @PathVariable("group-id") Long groupId);

    @PutMapping("/api/v1/groups/{group-id}/update")
    void updateGroup(@RequestHeader("X-USER-ID") Long userId,
                     @PathVariable("group-id") Long groupId,
                     @RequestBody GroupRequest request);

    @DeleteMapping("/api/v1/groups/{group-id}/delete")
    void deleteGroup(@RequestHeader("X-USER-ID") Long userId,
                     @PathVariable("group-id") Long groupId);
}
