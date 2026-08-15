package com.nhnacademy.insightonfront.adapter.core.groupmember;

import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberListResponse;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Core의 퍼블릭 그룹 멤버 API를 Gateway 경유로 호출한다.
 * <p><b>알려진 제약</b>: 다른 클라이언트들과 동일 — 로그인/세션 붙을 때 X-USER-ID 대신 Authorization
 * 헤더 전달로 바꿔야 실제로 끝까지 동작한다.
 */
@FeignClient(name = "insighton-gateway", contextId = "groupMemberClient", url = "${service-url.gateway}")
public interface GroupMemberClient {

    @GetMapping("/api/v1/groups/{group-id}/members")
    List<GroupMemberListResponse> getGroupMemberList(@RequestHeader("X-USER-ID") Long userId,
                                                       @PathVariable("group-id") Long groupId);

    @GetMapping("/api/v1/groups/{group-id}/members/{group-member-id}")
    GroupMemberResponse getGroupMember(@RequestHeader("X-USER-ID") Long userId,
                                       @PathVariable("group-id") Long groupId,
                                       @PathVariable("group-member-id") Long groupMemberId);

    @PutMapping("/api/v1/groups/{group-id}/members/{group-member-id}/toggle-manager")
    void toggleManagerRole(@RequestHeader("X-USER-ID") Long userId,
                           @PathVariable("group-id") Long groupId,
                           @PathVariable("group-member-id") Long groupMemberId);

    @PutMapping("/api/v1/groups/{group-id}/members/{group-member-id}/toggle-super-manager")
    void toggleSuperManagerRole(@RequestHeader("X-USER-ID") Long userId,
                                @PathVariable("group-id") Long groupId,
                                @PathVariable("group-member-id") Long groupMemberId);

    @DeleteMapping("/api/v1/groups/{group-id}/members/{group-member-id}/kick-member")
    void kickGroupMember(@RequestHeader("X-USER-ID") Long userId,
                         @PathVariable("group-id") Long groupId,
                         @PathVariable("group-member-id") Long groupMemberId);

    @DeleteMapping("/api/v1/groups/{group-id}/members/leave-group")
    void leaveGroup(@RequestHeader("X-USER-ID") Long userId,
                    @PathVariable("group-id") Long groupId);
}
