package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.groupmember.GroupMemberClient;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberListResponse;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/my-group")
public class GroupMemberController {
    private final GroupMemberClient groupMemberClient;

    @GetMapping("/member-list")
    public String getGroupMemberList(@CookieValue(value = "userId", required = false) Long userId,
                                     @CookieValue(value = "groupId", required = false) Long groupId, Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        List<GroupMemberListResponse> groupMemberList = groupMemberClient.getGroupMemberList(userId, groupId);

        model.addAttribute("groupMemberList", groupMemberList);

        return "";
    }

    @GetMapping("/members/{groupMemberId}")
    public String getGroupMember(@CookieValue(value = "userId", required = false) Long userId,
                                 @CookieValue(value = "groupId", required = false) Long groupId,
                                 @PathVariable("groupMemberId") Long groupMemberId, Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        GroupMemberResponse groupMember = groupMemberClient.getGroupMember(userId, groupId, groupMemberId);

        model.addAttribute("groupMember", groupMember);

        return "";
    }

    @PutMapping("/members/{groupMemberId}/toggle-manager")
    public String toggleManagerRole(@CookieValue(value = "userId", required = false) Long userId,
                                    @CookieValue(value = "groupId", required = false) Long groupId,
                                    @PathVariable("groupMemberId") Long groupMemberId) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        groupMemberClient.toggleManagerRole(userId, groupId, groupMemberId);

        log.info("멤버의 권한이 변경 되었습니다. Group ID : {}, groupMember ID : {}", groupId, groupMemberId);

        return "redirect:/my-group/members/" + groupMemberId;
    }

    @PutMapping("/members/{groupMemberId}/toggle-super-manager")
    public String toggleSuperManagerRole(@CookieValue(value = "userId", required = false) Long userId,
                                         @CookieValue(value = "groupId", required = false) Long groupId,
                                         @PathVariable("groupMemberId") Long groupMemberId) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        groupMemberClient.toggleSuperManagerRole(userId, groupId, groupMemberId);

        log.info("super manager의 권한이 양도 되었습니다. Group ID : {}, 새로운 super manager groupMember ID: {}", groupId, groupMemberId);

        return "redirect:/my-group/members/" + groupMemberId;
    }

    @DeleteMapping("/members/{groupMemberId}/kick")
    public String kickGroupMember(@CookieValue(value = "userId", required = false) Long userId,
                                  @CookieValue(value = "groupId", required = false) Long groupId,
                                  @PathVariable("groupMemberId") Long groupMemberId) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        groupMemberClient.kickGroupMember(userId, groupId, groupMemberId);

        log.info("멤버가 추방 되었습니다. Group ID : {}, 추방된 멤버 ID : {}", groupId, groupMemberId);

        return "redirect:/my-group/member-list";
    }

    @DeleteMapping("/leave-group")
    public String leaveGroup(@CookieValue(value = "userId", required = false) Long userId,
                             @CookieValue(value = "groupId", required = false) Long groupId) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        groupMemberClient.leaveGroup(userId, groupId);

        log.info("group({})을 떠나셨습니다... User ID : {}", groupId, userId);

        return "redirect:/";
    }
}
