package com.nhnacademy.insightonfront.adapter.core.group;

import com.nhnacademy.insightonfront.adapter.core.group.dto.GroupAdminResponse;
import com.nhnacademy.insightonfront.adapter.core.group.dto.GroupRequest;
import com.nhnacademy.insightonfront.adapter.core.group.dto.GroupResponse;
import com.nhnacademy.insightonfront.adapter.core.group.dto.MyGroupIdResponse;
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
 * userId는 안 넘긴다 — Authorization 헤더를 게이트웨이가 검증해서 X-User-Id로 바꿔 Core에
 * 넘겨주므로, front가 직접 X-USER-ID를 실어 보낼 필요가 없다.
 */
@FeignClient(name = "insighton-gateway", contextId = "groupClient", url = "${service-url.gateway}")
public interface GroupClient {

    /**
     * 로그인한 유저의 소속 groupId를 조회한다(한 유저 = 한 그룹). 로그인 직후 groupId를
     * 채우기 위해 호출한다. 아직 어느 그룹에도 속해있지 않으면 404.
     */
    @GetMapping("/api/v1/groups/my")
    MyGroupIdResponse getMyGroupId();

    @PostMapping("/api/v1/groups/create")
    void createGroup(@RequestBody GroupRequest request);

    @GetMapping("/api/v1/groups/{group-id}/my-group")
    GroupResponse getMyGroup(@PathVariable("group-id") Long groupId);

    @GetMapping("/api/v1/groups/{group-id}/preview")
    GroupResponse getGroupPreview(@PathVariable("group-id") Long groupId,
                                  @RequestParam("inviteToken") String inviteToken);

    /**
     * 이미 로그인한 유저가 초대 토큰만으로 그룹에 참가한다 — 회원가입 시점에 토큰을 넣는
     * {@code /internal/v1/groups/join-by-token}(Auth 전용 내부 API)과는 별개의, 나중에 다른
     * 그룹 초대를 받았을 때 쓰는 public API. 성공 시 200/빈 바디, 실패 시 잘못된/만료된 토큰이나
     * 이미 대기중인 가입신청이 있으면 400/409.
     */
    @PostMapping("/api/v1/groups/join")
    void joinGroup(@RequestParam("inviteToken") String inviteToken);

    @GetMapping("/api/v1/groups/admin/group-list")
    PageResponse<GroupAdminResponse> getGroupList(@RequestHeader("X-USER-ROLE") String userRole,
                                                   @RequestParam("page") int page,
                                                   @RequestParam("size") int size);

    @PutMapping("/api/v1/groups/{group-id}/invite-token/new")
    void newInviteToken(@PathVariable("group-id") Long groupId);

    @PutMapping("/api/v1/groups/{group-id}/update")
    void updateGroup(@PathVariable("group-id") Long groupId,
                     @RequestBody GroupRequest request);

    @DeleteMapping("/api/v1/groups/{group-id}/delete")
    void deleteGroup(@PathVariable("group-id") Long groupId);
}
