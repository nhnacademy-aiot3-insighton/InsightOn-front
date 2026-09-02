package com.nhnacademy.insightonfront.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Controller
public class HomeController {

    /**
     * 파비콘 파일을 두지 않으므로, 브라우저가 자동으로 보내는 /favicon.ico 요청은
     * 204로 조용히 응답한다(정적 리소스 404 → GlobalExceptionHandler 로 넘어가
     * 에러 로그가 쌓이는 것 방지).
     */
    @GetMapping("/favicon.ico")
    @ResponseBody
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }

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
