package com.nhnacademy.insightonfront.controller;

import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationResponse;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationStatus;
import com.nhnacademy.insightonfront.domain.groupregistration.service.GroupRegistrationStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final GroupRegistrationStatusService groupRegistrationStatusService;

    @GetMapping("/")
    public String home(@CookieValue(value = "accessToken", required = false) String accessToken,
                       @CookieValue(value = "userId", required = false) Long userId,
                       @CookieValue(value = "userName", required = false) String userNameEncoded,
                       Model model) {
        // 로그인 판단: accessToken 과 userId 가 있어야 로그인 상태
        if (accessToken == null || userId == null) {
            model.addAttribute("authState", "GUEST");
            return "index";
        }

        // 이름 쿠키는 URL 인코딩돼 있으니 디코드
        String userName = (userNameEncoded == null) ? null
                : URLDecoder.decode(userNameEncoded, StandardCharsets.UTF_8);
        model.addAttribute("userName", userName);

        // userId 는 쿠키에서 바로 사용 (그룹 조회)
        GroupRegistrationResponse latest = groupRegistrationStatusService.findLatest(userId);
        log.info("메인 페이지 로드: {} {}", userId, userName);
        boolean hasGroup = latest != null && latest.status() == GroupRegistrationStatus.APPROVED;
        log.info("hasGroup: {} ", hasGroup);
        model.addAttribute("authState", hasGroup ? "HAS_GROUP" : "NO_GROUP");
        return "index";
    }
}
