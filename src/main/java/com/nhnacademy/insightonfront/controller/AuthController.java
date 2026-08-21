package com.nhnacademy.insightonfront.controller;

import com.nhnacademy.insightonfront.client.AuthClient;
import com.nhnacademy.insightonfront.domain.auth.UserLoginRequest;
import com.nhnacademy.insightonfront.domain.auth.UserLoginResponse;
import feign.FeignException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 로그인/회원가입/이메일 인증/아이디 찾기/비밀번호 재설정/내 정보 — 인증·계정 관련 목업 페이지를
 * 전부 모아둔 컨트롤러. 실제 인증 서버(이메일 발송, BCrypt 검증, OAuth, 토큰 발급, 계정 DB)가
 * 아직 없어서, 세션에 상태를 흉내 내는 것으로 각 화면의 동작(재발송 쿨다운, 5회 실패 잠금,
 * 계정 상태 분기 등)을 실제로 눌러볼 수 있게 만들었다. 기능별로 //== 구획 주석으로 나눴다.
 *
 * <p>데모로 눌러볼 수 있는 조건들:
 * <ul>
 *   <li>회원가입 이메일 인증코드는 항상 {@value #DEMO_VERIFY_CODE}</li>
 *   <li>이메일 중복 확인은 {@value #DEMO_TAKEN_EMAIL} 만 "이미 가입됨"으로 처리</li>
 *   <li>로그인 비밀번호에 "wrong"을 입력하면 실패 처리(5회 반복 시 잠금)</li>
 *   <li>로그인 이메일이 dormant@ / banned@ / withdrawn@ 로 시작하면 각 계정 상태 분기를 볼 수 있음</li>
 * </ul>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthClient authClient;
    private final ObjectMapper objectMapper;

    private static final String DEMO_VERIFY_CODE = "123456";
    private static final String DEMO_TAKEN_EMAIL = "used@insighton.io";
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int VERIFY_LOCK_MINUTES = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOGIN_LOCK_MINUTES = 5;
    private static final int MAX_RESET_SENDS = 5;

    // ================================================================
    // 로그인
    // ================================================================

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        HttpServletResponse servletResponse,   // ★ 세션 대신 쿠키로 내림
                        Model model) {
        try {
            log.info("로그인 시도");
            ResponseEntity<String> response =
                    authClient.login(new UserLoginRequest(email, password));

            String accessToken = response.getBody();
            String refreshToken = extractCookie(response.getHeaders(), "refreshToken");
            String userName = extractUserName(accessToken);   // ★ 토큰에서 이름 꺼냄
            Long userId = extractUserId(accessToken);       // ★ 토큰에서 userId 꺼냄

            // accessToken 쿠키
            servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from("accessToken", accessToken)
                            .httpOnly(true).secure(true).path("/").sameSite("Lax")
                            .maxAge(Duration.ofMinutes(15))
                            .build().toString());

            // refreshToken 쿠키
            servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from("refreshToken", refreshToken)
                            .httpOnly(true).secure(true).path("/").sameSite("Lax")
                            .maxAge(Duration.ofDays(15))
                            .build().toString());

            // ★ userId 쿠키 (숫자라 인코딩 불필요)
            if (userId != null) {
                servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                        ResponseCookie.from("userId", userId.toString())
                                .httpOnly(true).secure(true).path("/").sameSite("Lax")
                                .maxAge(Duration.ofDays(15))
                                .build().toString());
            }

            // ★ userName 쿠키 추가 (한글이라 URL 인코딩 필수)
            if (userName != null) {
                servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                        ResponseCookie.from("userName",
                                        URLEncoder.encode(userName, StandardCharsets.UTF_8))
                                .httpOnly(true)      // 서버(Thymeleaf)에서만 읽음
                                .secure(true)
                                .path("/")
                                .sameSite("Lax")
                                .maxAge(Duration.ofDays(15))
                                .build().toString());
            }

            return "redirect:/";

        } catch (FeignException e) {
            int status = e.status();
            if (status <= 0) {
                model.addAttribute("loginError", "로그인 서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.");
            } else {
                model.addAttribute("loginError", loginErrorMessage(status));
            }
            return "login";
        } catch (RuntimeException e) {
            model.addAttribute("loginError", "로그인 서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.");
            return "login";
        }
    }

    /** accessToken(JWT)의 payload에서 sub 클레임(userId)을 꺼낸다. */
    public Long extractUserId(String accessToken) {
        try {
            String payload = accessToken.split("\\.")[1];
            String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            JsonNode claims = objectMapper.readTree(json);
            return claims.has("sub") ? claims.get("sub").asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String extractUserName(String accessToken) {
        try {
            String payload = accessToken.split("\\.")[1];
            String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            JsonNode claims = objectMapper.readTree(json);
            return claims.has("name") ? claims.get("name").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Set-Cookie 헤더에서 특정 쿠키 값만 뽑아낸다. */
    private String extractCookie(HttpHeaders headers, String name) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return null;
        }
        String prefix = name + "=";
        for (String cookie : setCookies) {
            for (String part : cookie.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith(prefix)) {
                    return trimmed.substring(prefix.length());
                }
            }
        }
        return null;
    }

    private String loginErrorMessage(int status) {
        return switch (status) {
            case 401, 400 -> "이메일 또는 비밀번호가 올바르지 않아요.";
            case 423      -> "로그인이 일시적으로 잠겼어요. 잠시 후 다시 시도해주세요.";
            default       -> "로그인에 실패했어요. 잠시 후 다시 시도해주세요.";
        };
    }

    @PostMapping("/logout")
    public String logout(@CookieValue(value = "accessToken", required = false) String accessToken,
                         @CookieValue(value = "userId", required = false) Long userId,
                         HttpServletResponse servletResponse) {
        log.info("로그아웃: {}", userId);
        // 1. auth 에 로그아웃 알림 (서버 측 토큰 무효화) — 실패해도 진행
        if (accessToken != null && userId != null) {
            try {
                authClient.logout("Bearer " + accessToken);
            } catch (Exception e) {
                // auth 로그아웃 실패해도 클라이언트 쿠키는 지운다
                log.warn("auth 로그아웃 실패: {}", e.getMessage());
            }
        }

        // 2. 브라우저 쿠키 삭제
        expireCookie(servletResponse, "accessToken");
        expireCookie(servletResponse, "refreshToken");
        expireCookie(servletResponse, "userId");
        expireCookie(servletResponse, "userName");

        return "redirect:/";
    }

    /** 같은 이름의 쿠키를 maxAge=0 으로 덮어써 삭제한다. */
    private void expireCookie(HttpServletResponse response, String name) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(name, "")
                        .httpOnly(true)
                        .secure(true)
                        .path("/")
                        .sameSite("Lax")
                        .maxAge(0)          // ★ 즉시 만료 = 삭제
                        .build().toString());
    }

    /**
     * 실제 Google OAuth(spring-oauth2-client + 클라이언트 등록)가 붙기 전까지, 같은 목업 세션을
     * 심는 것으로 대신한다. 버튼 자체는 실제로 클릭 가능하고 로그인 흐름을 그대로 타지만,
     * "구글 계정으로 인증됨"은 아직 아니다.
     */
    @PostMapping("/login/google")
    public String loginWithGoogle(HttpSession session) {
        session.setAttribute("userId", 1L);
        session.setAttribute("userEmail", "google-user@insighton.io");
        session.setAttribute("hasPassword", false);
        session.setAttribute("linkedProviders", new ArrayList<>(List.of("GOOGLE")));
        return "redirect:/";
    }

    /** GitHub도 동일 — 실제 OAuth 붙기 전 목업. */
    @PostMapping("/login/github")
    public String loginWithGithub(HttpSession session) {
        session.setAttribute("userId", 1L);
        session.setAttribute("userEmail", "github-user@insighton.io");
        session.setAttribute("hasPassword", false);
        session.setAttribute("linkedProviders", new ArrayList<>(List.of("GITHUB")));
        return "redirect:/";
    }

    // ================================================================
    // 회원가입
    // ================================================================

    @GetMapping("/signup")
    public String signupForm() {
        return "signup";
    }

//    @PostMapping("/signup")
//    public String signup(@RequestParam String email,
//                          @RequestParam String password,
//                          @RequestParam String name,
//                          @RequestParam String phone,
//                          @RequestParam(required = false) String emailVerified,
//                          HttpSession session,
//                          Model model) {
//        String verifiedEmail = (String) session.getAttribute("verifiedEmail");
//        if (!"true".equals(emailVerified) || !email.equalsIgnoreCase(verifiedEmail)) {
//            model.addAttribute("signupError", "이메일 인증을 먼저 완료해주세요.");
//            model.addAttribute("email", email);
//            model.addAttribute("name", name);
//            model.addAttribute("phone", phone);
//            return "signup";
//        }
//
//        // 실제 계정 저장(비밀번호 해시 등)은 백엔드가 붙으면 이 자리
//        log.info("[목업] 회원가입 완료 처리: {}", email);
//        session.removeAttribute("verifiedEmail");
//        session.removeAttribute("verifyAttempts");
//        session.removeAttribute("verifyLockUntil");
//        session.removeAttribute("verifyLastSentAt");
//        session.removeAttribute("verifyTargetEmail");
//        return "redirect:/login?registered=1";
//    }

    // ================================================================
    // 이메일 인증 (회원가입 전 — 중복 확인 / 코드 발송 / 코드 확인)
    // ================================================================

    @PostMapping("/signup/email/check-duplicate")
    @ResponseBody
    public Map<String, Object> checkEmailDuplicate(@RequestParam String email) {
        boolean available = !DEMO_TAKEN_EMAIL.equalsIgnoreCase(email.trim());
        return Map.of("available", available);
    }

    @PostMapping("/signup/email/send-code")
    @ResponseBody
    public Map<String, Object> sendVerificationCode(@RequestParam String email, HttpSession session) {
        Long lockUntil = (Long) session.getAttribute("verifyLockUntil");
        if (lockUntil != null && lockUntil > System.currentTimeMillis()) {
            return Map.of("ok", false, "message", "인증 시도가 잠겼어요. " + minutesRemaining(lockUntil) + "분 후 다시 시도해주세요.");
        }

        Long lastSent = (Long) session.getAttribute("verifyLastSentAt");
        if (lastSent != null && System.currentTimeMillis() - lastSent < RESEND_COOLDOWN_SECONDS * 1000L) {
            return Map.of("ok", false, "message", secondsRemaining(lastSent) + "초 후에 재발송할 수 있어요.");
        }

        session.setAttribute("verifyTargetEmail", email);
        session.setAttribute("verifyLastSentAt", System.currentTimeMillis());
        session.setAttribute("verifyAttempts", 0);
        session.removeAttribute("verifyLockUntil");
        log.info("[목업] {}로 인증코드 발송 (실제 이메일 발송 전 — 데모 코드는 항상 {})", email, DEMO_VERIFY_CODE);

        return Map.of("ok", true, "message", "인증코드를 보냈어요. 메일함을 확인해주세요.", "cooldownSeconds", RESEND_COOLDOWN_SECONDS);
    }

    @PostMapping("/signup/email/verify-code")
    @ResponseBody
    public Map<String, Object> verifyCode(@RequestParam String code, HttpSession session) {
        Long lockUntil = (Long) session.getAttribute("verifyLockUntil");
        if (lockUntil != null && lockUntil > System.currentTimeMillis()) {
            return Map.of("ok", false, "locked", true, "message", "인증 시도가 잠겼어요. " + minutesRemaining(lockUntil) + "분 후 다시 시도해주세요.");
        }

        String target = (String) session.getAttribute("verifyTargetEmail");
        if (target == null) {
            return Map.of("ok", false, "message", "인증코드를 먼저 발송해주세요.");
        }

        if (DEMO_VERIFY_CODE.equals(code.trim())) {
            session.setAttribute("verifiedEmail", target);
            session.removeAttribute("verifyAttempts");
            return Map.of("ok", true, "message", "이메일 인증이 완료됐어요.");
        }

        int attempts = intAttr(session, "verifyAttempts") + 1;
        session.setAttribute("verifyAttempts", attempts);
        if (attempts >= MAX_VERIFY_ATTEMPTS) {
            session.setAttribute("verifyLockUntil", System.currentTimeMillis() + VERIFY_LOCK_MINUTES * 60_000L);
            return Map.of("ok", false, "locked", true, "message", "5회 실패해서 " + VERIFY_LOCK_MINUTES + "분간 잠겼어요.");
        }
        return Map.of("ok", false, "message", "인증코드가 올바르지 않아요. (" + attempts + "/" + MAX_VERIFY_ATTEMPTS + ")");
    }

    // ================================================================
    // 아이디(이메일) 찾기 — 로그인 페이지의 모달에서 AJAX로 호출한다 (페이지 이동 없음)
    // ================================================================

    @PostMapping("/find-id")
    @ResponseBody
    public Map<String, Object> findId(@RequestParam String name, @RequestParam String phone) {
        // 실제 사용자 조회 전 목업 — 입력한 이름으로 그럴듯한 마스킹 이메일을 만들어 보여준다
        String demoEmail = name.toLowerCase().replaceAll("\\s", "") + "@insighton.io";
        return Map.of("maskedEmail", maskEmail(demoEmail));
    }

    // ================================================================
    // 비밀번호 재설정 — 요청 단계는 로그인 페이지 모달에서 AJAX로 호출한다.
    // 이메일로 받는 재설정 링크(/reset-password/confirm)는 로그인 페이지 맥락이 없는 채로
    // 열리니 그것만 별도 페이지로 남겨둔다.
    // ================================================================

    @PostMapping("/reset-password")
    @ResponseBody
    public Map<String, Object> requestReset(@RequestParam String email, HttpSession session) {
        Long lockUntil = (Long) session.getAttribute("resetLockUntil");
        if (lockUntil != null && lockUntil > System.currentTimeMillis()) {
            return Map.of("ok", false, "message", "요청이 너무 많아요. " + minutesRemaining(lockUntil) + "분 후 다시 시도해주세요.");
        }

        Long lastSent = (Long) session.getAttribute("resetLastSentAt");
        if (lastSent != null && System.currentTimeMillis() - lastSent < RESEND_COOLDOWN_SECONDS * 1000L) {
            return Map.of("ok", false, "message", secondsRemaining(lastSent) + "초 후에 다시 요청할 수 있어요.");
        }

        int sends = intAttr(session, "resetSendCount") + 1;
        session.setAttribute("resetSendCount", sends);
        session.setAttribute("resetLastSentAt", System.currentTimeMillis());
        if (sends >= MAX_RESET_SENDS) {
            session.setAttribute("resetLockUntil", System.currentTimeMillis() + LOGIN_LOCK_MINUTES * 60_000L);
        }
        // 계정 존재 여부와 무관하게 항상 같은 결과를 보여준다 — 가입 여부가 새어나가지 않도록
        log.info("[목업] {} 비밀번호 재설정 요청 처리 (실제 존재 여부는 항상 숨김)", email);
        return Map.of("ok", true, "message", "입력하신 이메일로 재설정 링크를 보냈어요. (가입되지 않은 이메일이어도 같은 안내가 표시돼요)");
    }

    @GetMapping("/reset-password/confirm")
    public String resetPasswordConfirmForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        return "reset-password-confirm";
    }

    @PostMapping("/reset-password/confirm")
    public String resetPasswordConfirm(@RequestParam String password,
                                        @RequestParam(required = false) String token,
                                        Model model) {
        // 실제 토큰 검증 및 비밀번호 저장은 백엔드가 붙으면 이 자리
        log.info("[목업] 토큰 {}로 비밀번호 재설정 완료 처리", token);
        model.addAttribute("done", true);
        return "reset-password-confirm";
    }

    // ================================================================
    // 내 정보
    // ================================================================

    @GetMapping("/mypage")
    public String myPage(@SessionAttribute(value = "userId", required = false) Long userId,
                          @SessionAttribute(value = "userEmail", required = false) String userEmail,
                          HttpSession session,
                          Model model) {
        if (userId == null) {
            return "redirect:/login";
        }
        model.addAttribute("email", userEmail != null ? userEmail : "user@insighton.io");
        model.addAttribute("name", attrOrDefault(session, "userName", "사용자"));
        model.addAttribute("phone", attrOrDefault(session, "userPhone", ""));
        model.addAttribute("linkedProviders", linkedProviders(session));
        model.addAttribute("hasPassword", hasPassword(session));
        return "mypage";
    }

    @PostMapping("/mypage/update")
    public String updateProfile(@SessionAttribute(value = "userId", required = false) Long userId,
                                 @RequestParam String name,
                                 @RequestParam String phone,
                                 HttpSession session) {
        if (userId == null) {
            return "redirect:/login";
        }
        session.setAttribute("userName", name);
        session.setAttribute("userPhone", phone);
        return "redirect:/mypage";
    }

    @PostMapping("/mypage/password")
    public String changePassword(@SessionAttribute(value = "userId", required = false) Long userId,
                                  @RequestParam String currentPassword,
                                  @RequestParam String newPassword,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        if (userId == null) {
            return "redirect:/login";
        }
        if (!hasPassword(session)) {
            redirectAttributes.addFlashAttribute("passwordError", "소셜 로그인 전용 계정은 비밀번호를 바꿀 수 없어요.");
            return "redirect:/mypage";
        }
        // 실제 현재 비밀번호 검증 전 목업 — "wrong"을 입력하면 실패 흐름을 볼 수 있다
        if ("wrong".equals(currentPassword)) {
            redirectAttributes.addFlashAttribute("passwordError", "현재 비밀번호가 올바르지 않아요.");
            return "redirect:/mypage";
        }
        log.info("[목업] 비밀번호 변경 처리");
        redirectAttributes.addFlashAttribute("passwordSuccess", "비밀번호를 바꿨어요.");
        return "redirect:/mypage";
    }

    @PostMapping("/mypage/social/link")
    public String linkSocial(@SessionAttribute(value = "userId", required = false) Long userId,
                              @RequestParam String provider,
                              HttpSession session) {
        if (userId == null) {
            return "redirect:/login";
        }
        List<String> linked = linkedProviders(session);
        if (!linked.contains(provider)) {
            linked.add(provider);
            session.setAttribute("linkedProviders", linked);
        }
        return "redirect:/mypage";
    }

    @PostMapping("/mypage/social/unlink")
    public String unlinkSocial(@SessionAttribute(value = "userId", required = false) Long userId,
                                @RequestParam String provider,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        if (userId == null) {
            return "redirect:/login";
        }
        List<String> linked = linkedProviders(session);
        boolean isLastMethod = linked.size() <= 1 && !hasPassword(session);
        if (isLastMethod) {
            redirectAttributes.addFlashAttribute("socialError", "마지막 남은 로그인 수단은 해제할 수 없어요.");
            return "redirect:/mypage";
        }
        linked.remove(provider);
        session.setAttribute("linkedProviders", linked);
        return "redirect:/mypage";
    }

    // ================================================================
    // 공용 헬퍼
    // ================================================================

    private int incrementLoginFail(HttpSession session) {
        int fails = intAttr(session, "loginFailCount") + 1;
        session.setAttribute("loginFailCount", fails);
        if (fails >= MAX_LOGIN_ATTEMPTS) {
            session.setAttribute("loginLockUntil", System.currentTimeMillis() + LOGIN_LOCK_MINUTES * 60_000L);
            session.removeAttribute("loginFailCount");
        }
        return fails;
    }

    private String loginFailMessage(int fails) {
        if (fails >= MAX_LOGIN_ATTEMPTS) {
            return "5회 연속 실패해서 " + LOGIN_LOCK_MINUTES + "분간 로그인이 잠겼어요.";
        }
        return "이메일 또는 비밀번호가 올바르지 않아요. (" + fails + "/" + MAX_LOGIN_ATTEMPTS + ")";
    }

    /** 실제 계정 상태 조회 전 목업 — 이메일 접두어로 휴면/차단/탈퇴 분기를 흉내 낸다. */
    private String accountStatusFor(String email) {
        String lower = email.toLowerCase();
        if (lower.startsWith("dormant@")) return "DORMANT";
        if (lower.startsWith("banned@")) return "BANNED";
        if (lower.startsWith("withdrawn@")) return "WITHDRAWN";
        return null;
    }

    private String statusMessage(String status) {
        return switch (status) {
            case "DORMANT" -> "휴면 계정이에요. 본인 확인 후 이용할 수 있어요.";
            case "BANNED" -> "이용이 제한된 계정이에요. 고객센터로 문의해주세요.";
            case "WITHDRAWN" -> "탈퇴한 계정이에요. 새로 가입해주세요.";
            default -> "로그인할 수 없는 계정이에요.";
        };
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return email;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "*".repeat(Math.max(local.length() - visible.length(), 2)) + domain;
    }

    @SuppressWarnings("unchecked")
    private List<String> linkedProviders(HttpSession session) {
        List<String> linked = (List<String>) session.getAttribute("linkedProviders");
        if (linked == null) {
            linked = new ArrayList<>();
            session.setAttribute("linkedProviders", linked);
        }
        return linked;
    }

    private boolean hasPassword(HttpSession session) {
        Object v = session.getAttribute("hasPassword");
        return v == null || (Boolean) v;
    }

    private String attrOrDefault(HttpSession session, String key, String defaultValue) {
        Object v = session.getAttribute(key);
        return v != null ? (String) v : defaultValue;
    }

    private int intAttr(HttpSession session, String key) {
        Object v = session.getAttribute(key);
        return v != null ? (Integer) v : 0;
    }

    private long minutesRemaining(long untilEpochMillis) {
        return (untilEpochMillis - System.currentTimeMillis()) / 60_000 + 1;
    }

    private long secondsRemaining(long sinceEpochMillis) {
        return RESEND_COOLDOWN_SECONDS - (System.currentTimeMillis() - sinceEpochMillis) / 1000;
    }
}
