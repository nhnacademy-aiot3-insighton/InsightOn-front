package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.group.GroupClient;
import com.nhnacademy.insightonfront.adapter.core.group.dto.GroupAdminResponse;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.GroupRegistrationClient;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationResponse;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationStatus;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Objects;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {
    private final GroupClient groupClient;
    private final GroupRegistrationClient groupRegistrationClient;

    @GetMapping("/group-list")
    public String getGroupList(@RequestHeader String userRole,
                               @RequestParam("page") int page,
                               @RequestParam("size") int size, Model model) {
        PageResponse<GroupAdminResponse> adminGroupList = groupClient.getGroupList(userRole, page, size);

        model.addAttribute("groupList", adminGroupList);

        return "";
    }

    @GetMapping("/group-registrations")
    public String getGroupRegistrations(@CookieValue(value = "accessToken", required = false) String accessToken,
                                        @RequestParam(required = false) GroupRegistrationStatus status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        Model model
    ) {
        if (Objects.isNull(accessToken)) {
            return "redirect:/admin/login";
        }

        try {
            PageResponse<GroupRegistrationResponse> registrations = groupRegistrationClient.getGroupRegistrations(status, page, size, "groupRegistrationId,asc");
            model.addAttribute("registrations", registrations);
            model.addAttribute("status", status);
            return "admin/group-registrations";
        } catch (FeignException.Forbidden e) {
            return "redirect:/";
        }
    }

    @PostMapping("/group-registrations/{id}/approve")
    public String approve(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            groupRegistrationClient.approveGroupRegistration(id);
        } catch (FeignException.Forbidden e) {
            return "redirect:/";
        } catch (FeignException.Conflict e) {
            redirectAttributes.addFlashAttribute("adminError", "이미 다른 관리자가 처리한 신청이에요.");
        }
        return "redirect:/admin/group-registrations";
    }

    @PostMapping("/group-registrations/{id}/reject")
    public String reject(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            groupRegistrationClient.rejectGroupRegistration(id);
        } catch (FeignException.Forbidden e) {
            return "redirect:/";
        } catch (FeignException.Conflict e) {
            redirectAttributes.addFlashAttribute("adminError", "이미 다른 관리자가 처리한 신청이에요.");
        }
        return "redirect:/admin/group-registrations";
    }
}
