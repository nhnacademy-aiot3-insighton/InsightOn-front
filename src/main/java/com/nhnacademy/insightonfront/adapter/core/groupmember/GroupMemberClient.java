package com.nhnacademy.insightonfront.adapter.core.groupmember;

import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberListResponse;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Core의 퍼블릭 그룹 멤버 API를 Gateway 경유로 호출한다.
 * userId는 안 넘긴다 — 게이트웨이가 Authorization을 검증해서 X-User-Id로 바꿔 Core에 넘겨준다.
 */
@FeignClient(name = "insighton-gateway", contextId = "groupMemberClient", url = "${service-url.gateway}")
public interface GroupMemberClient {

    @PostMapping("/api/v1/groups/{group-id}/members/invite")
    void inviteMemberByEmail(@PathVariable("group-id") Long groupId, @RequestParam("email") String email);

    @GetMapping("/api/v1/groups/{group-id}/members")
    List<GroupMemberListResponse> getGroupMemberList(@PathVariable("group-id") Long groupId);

    @GetMapping("/api/v1/groups/{group-id}/members/{group-member-id}")
    GroupMemberResponse getGroupMember(@PathVariable("group-id") Long groupId,
                                       @PathVariable("group-member-id") Long groupMemberId);

    @PutMapping("/api/v1/groups/{group-id}/members/{group-member-id}/toggle-manager")
    void toggleManagerRole(@PathVariable("group-id") Long groupId,
                           @PathVariable("group-member-id") Long groupMemberId);

    @PutMapping("/api/v1/groups/{group-id}/members/{group-member-id}/toggle-super-manager")
    void toggleSuperManagerRole(@PathVariable("group-id") Long groupId,
                                @PathVariable("group-member-id") Long groupMemberId);

    @DeleteMapping("/api/v1/groups/{group-id}/members/{group-member-id}/kick-member")
    void kickGroupMember(@PathVariable("group-id") Long groupId,
                         @PathVariable("group-member-id") Long groupMemberId);

    @DeleteMapping("/api/v1/groups/{group-id}/members/leave-group")
    void leaveGroup(@PathVariable("group-id") Long groupId);
}
