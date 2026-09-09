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

    // /favicon.ico 는 src/main/resources/static/favicon.ico 를 스프링부트 정적 리소스
    // 핸들러가 그대로 서빙한다. (예전엔 파비콘 파일이 없어서 204로 눌렀는데,
    // 파일이 생겨서 그 핸들러를 제거함 — 남겨두면 정적 파일보다 우선해서 아이콘이 안 뜬다.)

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

        // 이름 쿠키는 URL 인코딩돼 있으니 디코드
        String userName = (userNameEncoded == null) ? null
                : URLDecoder.decode(userNameEncoded, StandardCharsets.UTF_8);
        model.addAttribute("userName", userName);

        // groupId 는 로그인 시점에 GroupMember 조회로 쿠키에 캐싱해둔 실제 소속 여부.
        // "/" 는 강제 리다이렉트하지 않고 항상 랜딩을 렌더한다(사용자가 언제든 사이트를
        // 둘러볼 수 있어야 하고, /my-group 이 일시적으로 죽어도 갇히지 않도록).
        // 그룹 보유자는 랜딩 CTA 가 "대시보드로 이동"으로 바뀌고,
        // 로그인 직후 대시보드로 보내는 건 AuthController.login 이 담당한다.
        model.addAttribute("authState", groupId != null ? "HAS_GROUP" : "NO_GROUP");
        return "index";
    }
}
