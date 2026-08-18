package com.nhnacademy.insightonfront.controller;

import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationResponse;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationStatus;
import com.nhnacademy.insightonfront.domain.groupregistration.service.GroupRegistrationStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final GroupRegistrationStatusService groupRegistrationStatusService;

    @GetMapping("/")
    public String home(@SessionAttribute(value = "userId", required = false) Long userId, Model model) {
        if (userId == null) {
            model.addAttribute("authState", "GUEST");
            return "index";
        }
        GroupRegistrationResponse latest = groupRegistrationStatusService.findLatest(userId);
        boolean hasGroup = latest != null && latest.status() == GroupRegistrationStatus.APPROVED;
        model.addAttribute("authState", hasGroup ? "HAS_GROUP" : "NO_GROUP");
        return "index";
    }
}
