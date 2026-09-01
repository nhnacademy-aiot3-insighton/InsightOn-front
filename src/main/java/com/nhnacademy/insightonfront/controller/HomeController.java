package com.nhnacademy.insightonfront.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(@CookieValue(value = "accessToken", required = false) String accessToken,
                       @CookieValue(value = "userId", required = false) Long userId,
                       @CookieValue(value = "userName", required = false) String userNameEncoded,
                       @CookieValue(value = "groupId", required = false) Long groupId,
                       Model model) {
        // 로그인 판단: accessToken 과 userId 가 있어야 로그인 상태
        if (accessToken == null || userId == null) {
            model.addAttribute("authState", "GUEST");
            return "index";
        }

        // groupId는 로그인 시점에 GroupMember 조회로 쿠키에 캐싱해둔 값 — 신청 이력이 아니라
        // 실제 소속 여부로 판단한다. 이미 그룹에 속한 사용자는 랜딩을 거치지 않고 바로 대시보드로.
        if (groupId != null) {
            return "redirect:/my-group";
        }

        // 이름 쿠키는 URL 인코딩돼 있으니 디코드
        String userName = (userNameEncoded == null) ? null
                : URLDecoder.decode(userNameEncoded, StandardCharsets.UTF_8);
        model.addAttribute("userName", userName);

        // 그룹 미가입 로그인 사용자 — 랜딩에서 그룹 생성/초대 코드 입력을 안내한다.
        model.addAttribute("authState", "NO_GROUP");
        return "index";
    }
}
