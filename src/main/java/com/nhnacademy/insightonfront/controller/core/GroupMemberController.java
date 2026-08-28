package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.groupmember.GroupMemberClient;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberListResponse;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * groupId는 쿠키에서 읽는다(한 유저 = 한 그룹). groupMemberId는 그룹 안에서 특정 멤버를
 * 가리키는 값이라 세션이 아니라 경로 변수로 받는다.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/my-group/members")
public class GroupMemberController {
    private final GroupMemberClient groupMemberClient;
    private final com.nhnacademy.insightonfront.common.service.GroupPermissionService groupPermissionService;

    @GetMapping
    public String getGroupMemberList(@CookieValue(value = "groupId", required = false) Long groupId, Model model) {
        if (groupId == null) {
            return "redirect:/group-registration";
        }
        try {
            List<GroupMemberListResponse> groupMemberList = groupMemberClient.getGroupMemberList(groupId);
            model.addAttribute("groupMemberList", groupMemberList);
        } catch (FeignException e) {
            log.warn("그룹 멤버 목록 조회 실패 - groupId:{}", groupId, e);
            model.addAttribute("groupMemberList", null);
        }

        return "groupmember/list";
    }

    @GetMapping("/{group-member-id}")
    public String getGroupMember(@CookieValue(value = "userId", required = false) Long userId,
                                 @CookieValue(value = "groupId", required = false) Long groupId,
                                 @PathVariable("group-member-id") Long groupMemberId, Model model) {
        if (groupId == null) {
            return "redirect:/group-registration";
        }
        try {
            GroupMemberResponse groupMember = groupMemberClient.getGroupMember(groupId, groupMemberId);
            model.addAttribute("groupMember", groupMember);

            boolean isSuperManager = false;
            if (userId != null) {
                isSuperManager = groupPermissionService.isSuperManager(groupId, userId);
            }
            model.addAttribute("isSuperManager", isSuperManager);

        } catch (FeignException e) {
            log.warn("그룹 멤버 상세 조회 실패 - groupId:{}, groupMemberId:{}", groupId, groupMemberId, e);
            model.addAttribute("groupMember", null);
        }

        return "groupmember/detail";
    }

    @PutMapping("/{group-member-id}/toggle-manager")
    public String toggleManagerRole(@CookieValue("groupId") Long groupId,
                                    @PathVariable("group-member-id") Long groupMemberId) {

        groupMemberClient.toggleManagerRole(groupId, groupMemberId);

        log.info("멤버의 권한이 변경 되었습니다. Group ID : {}, groupMember ID : {}", groupId, groupMemberId);

        return "redirect:/my-group/members/" + groupMemberId;
    }

    @PutMapping("/{group-member-id}/toggle-super-manager")
    public String toggleSuperManagerRole(@CookieValue("groupId") Long groupId,
                                         @PathVariable("group-member-id") Long groupMemberId) {
        groupMemberClient.toggleSuperManagerRole(groupId, groupMemberId);

        log.info("super manager의 권한이 양도 되었습니다. Group ID : {}, 새로운 super manager groupMember ID: {}", groupId, groupMemberId);

        return "redirect:/my-group/members/" + groupMemberId;
    }

    @DeleteMapping("/{group-member-id}/kick")
    public String kickGroupMember(@CookieValue("groupId") Long groupId,
                                  @PathVariable("group-member-id") Long groupMemberId) {

        groupMemberClient.kickGroupMember(groupId, groupMemberId);

        log.info("멤버가 추방 되었습니다. Group ID : {}, 추방된 멤버 ID : {}", groupId, groupMemberId);

        return "redirect:/my-group/member";
    }

    @DeleteMapping("/leave")
    public String leaveGroup(@CookieValue("groupId") Long groupId) {

        groupMemberClient.leaveGroup(groupId);

        log.info("group({})을 떠나셨습니다.", groupId);

        return "redirect:/";
    }
}
