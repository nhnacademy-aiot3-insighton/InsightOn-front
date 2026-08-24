package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.group.GroupClient;
import com.nhnacademy.insightonfront.adapter.core.group.dto.GroupAdminResponse;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {
    private final GroupClient groupClient;

    @GetMapping("/group-list")
    public String getGroupList(@RequestHeader String userRole,
                               @RequestParam("page") int page,
                               @RequestParam("size") int size, Model model) {
        PageResponse<GroupAdminResponse> adminGroupList = groupClient.getGroupList(userRole, page, size);

        model.addAttribute("groupList", adminGroupList);

        return "";
    }
}
