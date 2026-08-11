package com.nhnacademy.insightonfront.controller;

import com.nhnacademy.insightonfront.client.core.CoreApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final CoreApiClient coreApiClient;

    @GetMapping("/dashboard")
    public String getDashboard(Long userId,
//                               @PathVariable("group-id") Long groupId,
//                               @PathVariable("location-id") Long locationId,
                               Model model
    ) {

//        DashboardResponse response = coreApiClient.getDashboard(userId, groupId, locationId);

//        model.addAttribute("dashboard", response);

        return "dashboard/widgets";
    }
}
