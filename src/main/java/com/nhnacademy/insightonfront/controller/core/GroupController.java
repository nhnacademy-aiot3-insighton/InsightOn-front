package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.group.GroupClient;
import com.nhnacademy.insightonfront.adapter.core.group.dto.GroupAdminResponse;
import com.nhnacademy.insightonfront.adapter.core.group.dto.GroupRequest;
import com.nhnacademy.insightonfront.adapter.core.group.dto.GroupResponse;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/groups")
public class GroupController {
    private final GroupClient groupClient;

    @PostMapping("/groups/create")
    public String createGroup(@RequestHeader Long userId,
                              @RequestBody GroupRequest request) {

        groupClient.createGroup(userId, request);

        return "redirect:/";
    }

    @GetMapping("/{group-id}/my-group")
    public String getMyGroup(@RequestHeader Long userId,
                             @PathVariable("group-id") Long groupId,
                             Model model) {
        GroupResponse myGroup = groupClient.getMyGroup(userId, groupId);

        model.addAttribute("myGroup", myGroup);

        return "";
    }

    @GetMapping("/{group-id}/preview")
    public String getGroupPreview(@RequestHeader Long userId,
                                  @PathVariable("group-id") Long groupId,
                                  @RequestParam("inviteToken") String inviteToken,
                                  Model model) {

        GroupResponse groupPreview = groupClient.getGroupPreview(userId, groupId, inviteToken);

        model.addAttribute("groupPreview", groupPreview);

        return "";
    }

    @GetMapping("/admin/group-list")
    public String getGroupList(@RequestHeader Long userId,
                               @RequestHeader String userRole,
                               @RequestParam("page") int page,
                               @RequestParam("size") int size, Model model) {
        PageResponse<GroupAdminResponse> adminGroupList = groupClient.getGroupList(userRole, userId, page, size);

        model.addAttribute("groupList", adminGroupList);

        return "";
    }

    @PutMapping("/{group-id}/invite-token/new")
    public String newInviteToken(@RequestHeader Long userId,
                                 @PathVariable("group-id") Long groupId) {

        groupClient.newInviteToken(userId, groupId);

        log.info("토큰이 새로 발급되었습니다. Group ID : {}", groupId);

        return "redirect:/groups/" + groupId + "/my-group";
    }

    @PutMapping("/{group-id}/update")
    public String updateGroup(@RequestHeader Long userId,
                              @PathVariable("group-id") Long groupId,
                              @RequestBody GroupRequest request) {

        groupClient.updateGroup(userId, groupId, request);

        return "redirect:/groups/" + groupId + "/my-group";
    }

    @DeleteMapping("/{group-id}/delete")
    public String deleteGroup(@RequestHeader Long userId,
                              @PathVariable("group-id") Long groupId) {

        groupClient.deleteGroup(userId, groupId);

        log.info("성공적으로 삭제되었습니다. Group ID : {}", groupId);
        return "redirect:/";
    }
}
