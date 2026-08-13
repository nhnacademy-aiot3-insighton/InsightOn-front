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
@RequestMapping("/groups/{group-id}/members")
public class GroupMemberController {
    private final GroupMemberClient groupMemberClient;

    @GetMapping
    public String getGroupMemberList(@RequestHeader Long userId,
                                     @PathVariable("group-id") Long groupId, Model model) {
        List<GroupMemberListResponse> groupMemberList = groupMemberClient.getGroupMemberList(userId, groupId);

        model.addAttribute("groupMemberList", groupMemberList);

        return "";
    }

    @GetMapping("/{group-member-id}")
    public String getGroupMember(@RequestHeader Long userId,
                                 @PathVariable("group-id") Long groupId,
                                 @PathVariable("group-member-id") Long groupMemberId, Model model) {
        GroupMemberResponse groupMember = groupMemberClient.getGroupMember(userId, groupId, groupMemberId);

        model.addAttribute("groupMember", groupMember);

        return "";
    }

    @PutMapping("/{group-member-id}/toggle-manager")
    public String toggleManagerRole(@RequestHeader Long userId,
                                    @PathVariable("group-id") Long groupId,
                                    @PathVariable("group-member-id") Long groupMemberId) {

        groupMemberClient.toggleManagerRole(userId, groupId, groupMemberId);

        log.info("멤버의 권한이 변경 되었습니다. Group ID : {}, groupMember ID : {}", groupId, groupMemberId);

        return "redirect:/groups/" + groupId + "/members/" + groupMemberId;
    }

    @PutMapping("/{group-member-id}/toggle-super-manager")
    public String toggleSuperManagerRole(@RequestHeader Long userId,
                                         @PathVariable("group-id") Long groupId,
                                         @PathVariable("group-member-id") Long groupMemberId) {
        groupMemberClient.toggleSuperManagerRole(userId, groupId, groupMemberId);

        log.info("super manager의 권한이 양도 되었습니다. Group ID : {}, 새로운 super manager groupMember ID: {}", groupId, groupMemberId);

        return "redirect:/groups/" + groupId + "/members/" + groupMemberId;
    }

    @DeleteMapping("/{group-member-id}/kick-member")
    public String kickGroupMember(@RequestHeader Long userId,
                                  @PathVariable("group-id") Long groupId,
                                  @PathVariable("group-member-id") Long groupMemberId) {

        groupMemberClient.kickGroupMember(userId, groupId, groupMemberId);

        log.info("멤버가 추방 되었습니다. Group ID : {}, 추방된 멤버 ID : {}", groupId, groupMemberId);

        return "redirect:/groups/" + groupId + "/members";
    }

    @DeleteMapping("/leave-group")
    public String leaveGroup(@RequestHeader Long userId,
                             @PathVariable("group-id") Long groupId) {

        groupMemberClient.leaveGroup(userId, groupId);

        log.info("group({})을 떠나셨습니다... User ID : {}", groupId, userId);

        return "redirect:/";
    }
}
