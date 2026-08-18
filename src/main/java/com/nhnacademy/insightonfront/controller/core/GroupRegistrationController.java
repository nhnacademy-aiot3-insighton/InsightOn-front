package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.groupregistration.GroupRegistrationClient;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.CreateGroupRegistrationRequest;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationResponse;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationStatus;
import com.nhnacademy.insightonfront.adapter.core.region.RegionClient;
import com.nhnacademy.insightonfront.domain.groupregistration.service.GroupRegistrationStatusService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * 그룹이 없는 사용자가 로그인 후 도착하는 곳 — 신청서를 작성하거나, 이미 낸 신청의 승인 대기
 * 상태를 본다. "이 사용자가 그룹을 이미 갖고 있는지"를 직접 조회하는 API는 아직 없어서, 가장
 * 최근 그룹 신청의 상태로 판단한다: APPROVED면 그룹으로, PENDING이면 대기 화면으로, 그 외
 * (신청 이력 없음 / REJECTED / CANCELLED)면 신청서를 보여준다.
 * <p>state/city는 core의 행정구역 레지스트리 기준으로만 유효한 조합이 통과되므로(없는 조합은 400),
 * 자유 입력 대신 {@link RegionClient}로 받아온 목록에서만 고르게 한다.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/group-registration")
public class GroupRegistrationController {

    private final GroupRegistrationClient groupRegistrationClient;
    private final GroupRegistrationStatusService groupRegistrationStatusService;
    private final RegionClient regionClient;

    @GetMapping
    public String view(@SessionAttribute(value = "userId", required = false) Long userId, Model model) {
        if (userId == null) {
            return "redirect:/login";
        }

        GroupRegistrationResponse latest = groupRegistrationStatusService.findLatest(userId);

        if (latest != null && latest.status() == GroupRegistrationStatus.APPROVED) {
            return "redirect:/my-group";
        }

        if (latest != null && latest.status() == GroupRegistrationStatus.PENDING) {
            model.addAttribute("state", "PENDING");
            model.addAttribute("registration", latest);
        } else {
            model.addAttribute("state", "FORM");
            model.addAttribute("previous", latest);
            model.addAttribute("states", regionClient.getStates());
        }
        return "group/registration";
    }

    @PostMapping
    public String submit(@SessionAttribute(value = "userId", required = false) Long userId,
                          @RequestParam String groupName,
                          @RequestParam String state,
                          @RequestParam String city,
                          @RequestParam(required = false) String description) {
        if (userId == null) {
            return "redirect:/login";
        }
        groupRegistrationClient.createRequest(userId,
                new CreateGroupRegistrationRequest(groupName, description, state, city));
        return "redirect:/group-registration";
    }

    @PostMapping("/cancel")
    public String cancel(@SessionAttribute(value = "userId", required = false) Long userId,
                          @RequestParam Long groupRegistrationId) {
        if (userId == null) {
            return "redirect:/login";
        }
        groupRegistrationClient.cancelGroupRegistration(userId, groupRegistrationId);
        return "redirect:/group-registration";
    }

    /** 신청 폼의 시/도 선택 시 시/군/구 옵션을 채우는 AJAX. */
    @GetMapping("/cities")
    @ResponseBody
    public List<String> cities(@RequestParam String state) {
        return regionClient.getCities(state);
    }
}
