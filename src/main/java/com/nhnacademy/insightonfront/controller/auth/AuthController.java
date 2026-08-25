package com.nhnacademy.insightonfront.controller.auth;

import com.nhnacademy.insightonfront.adapter.auth.signup.dto.UserSignupResponse;
import com.nhnacademy.insightonfront.adapter.core.group.GroupClient;
import com.nhnacademy.insightonfront.auth.AccessTokenContext;
import com.nhnacademy.insightonfront.adapter.auth.auth.AuthClient;
import com.nhnacademy.insightonfront.domain.auth.dto.UserLoginRequest;
import com.nhnacademy.insightonfront.domain.mypage.MypageService;
import com.nhnacademy.insightonfront.domain.signup.SignupService;
import com.nhnacademy.insightonfront.domain.signup.dto.*;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.http.HttpStatus;
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
    private final SignupService signupService;
    private final MypageService mypageService;
    private final GroupClient groupClient;
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
                        HttpServletRequest servletRequest,
                        HttpServletResponse servletResponse,
                        HttpSession session,
                        Model model) {
        try {
            log.info("[Auth] 로그인 시도: email={}", maskEmail(email));
            ResponseEntity<String> response =
                    authClient.login(new UserLoginRequest(email, password));

            String accessToken = response.getBody();
            String refreshToken = extractCookie(response.getHeaders(), "refreshToken");
            String userName = extractUserName(accessToken);
            Long userId = extractUserId(accessToken);
            boolean secure = servletRequest.isSecure();

            servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from("accessToken", accessToken)
                            .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                            .maxAge(Duration.ofMinutes(15))
                            .build().toString());

            servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from("refreshToken", refreshToken)
                            .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                            .maxAge(Duration.ofDays(15))
                            .build().toString());

            if (userId != null) {
                servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                        ResponseCookie.from("userId", userId.toString())
                                .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                                .maxAge(Duration.ofDays(15))
                                .build().toString());
            }

            if (userName != null) {
                servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                        ResponseCookie.from("userName",
                                        URLEncoder.encode(userName, StandardCharsets.UTF_8))
                                .httpOnly(true)
                                .secure(secure)
                                .path("/")
                                .sameSite("Lax")
                                .maxAge(Duration.ofDays(15))
                                .build().toString());
            }

            Long groupId = resolveGroupId(accessToken);
            if (groupId != null) {
                servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                        ResponseCookie.from("groupId", groupId.toString())
                                .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                                .maxAge(Duration.ofDays(15))
                                .build().toString());
            }

            log.info("[Auth] 로그인 시도 성공: email={}", maskEmail(email));

            return "redirect:/";

        } catch (FeignException e) {
            log.warn("[Auth] 로그인 처리 중 FeignException: status={}", e.status(), e);
            int status = e.status();
            if (status <= 0) {
                model.addAttribute("loginError", "로그인 서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.");
            } else {
                model.addAttribute("loginError", loginErrorMessage(status));
            }
            return "login";
        } catch (RuntimeException e) {
            log.warn("[Auth] 로그인 처리 중 예외 발생", e);
            model.addAttribute("loginError", "로그인 서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.");
            return "login";
        }
    }

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

    private Long resolveGroupId(String accessToken) {
        try {
            AccessTokenContext.set(accessToken);
            return groupClient.getMyGroupId().groupId();
        } catch (FeignException.NotFound e) {
            return null;
        } finally {
            AccessTokenContext.clear();
        }
    }

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
            case 423 -> "로그인이 일시적으로 잠겼어요. 잠시 후 다시 시도해주세요.";
            default -> "로그인에 실패했어요. 잠시 후 다시 시도해주세요.";
        };
    }

    @PostMapping("/logout")
    public String logout(@CookieValue(value = "accessToken", required = false) String accessToken,
                         @CookieValue(value = "userId", required = false) Long userId,
                         HttpServletRequest servletRequest,
                         HttpServletResponse servletResponse) {
        log.info("로그아웃: {}", userId);
        if (accessToken != null && userId != null) {
            try {
                authClient.logout();
            } catch (Exception e) {
                log.warn("[Auth] 로그아웃 실패: {}", e.getMessage());
            }
        }

        boolean secure = servletRequest.isSecure();
        expireCookie(servletResponse, "accessToken", secure);
        expireCookie(servletResponse, "refreshToken", secure);
        expireCookie(servletResponse, "userId", secure);
        expireCookie(servletResponse, "userName", secure);
        expireCookie(servletResponse, "groupId", secure);

        return "redirect:/";
    }

    private void expireCookie(HttpServletResponse response, String name, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(name, "")
                        .httpOnly(true)
                        .secure(secure)
                        .path("/")
                        .sameSite("Lax")
                        .maxAge(0)
                        .build().toString());
    }

    @PostMapping("/login/google")
    public String loginWithGoogle(HttpSession session) {
        session.setAttribute("userId", 1L);
        session.setAttribute("userEmail", "google-user@insighton.io");
        session.setAttribute("hasPassword", false);
        session.setAttribute("linkedProviders", new ArrayList<>(List.of("GOOGLE")));
        return "redirect:/";
    }

    @PostMapping("/login/github")
    public String loginWithGithub(HttpSession session) {
        session.setAttribute("userId", 1L);
        session.setAttribute("userEmail", "github-user@insighton.io");
        session.setAttribute("hasPassword", false);
        session.setAttribute("linkedProviders", new ArrayList<>(List.of("GITHUB")));
        return "redirect:/";
    }

    // ================================================================
    // 회원가입 — SignupService를 통해 auth로 위임
    // ================================================================

    @GetMapping("/signup")
    public String signupForm() {
        return "signup";
    }

    /** 이메일 중복 확인 */
    @PostMapping("/signup/check-email")
    @ResponseBody
    public Map<String, Object> checkEmailDuplicate(@RequestBody EmailAvailableRequest request) {
        log.info("[Signup] 이메일 중복 확인: email={}", maskEmail(request.email()));
        boolean available = signupService.checkEmailAvailable(request.email());
        return Map.of("available", available);
    }

    /** 이메일 인증 코드 요청 */
    @PostMapping("/signup/send-code")
    @ResponseBody
    public ResponseEntity<Void> sendVerificationCode(@RequestBody EmailVerifyRequest request) {
        log.info("[Signup] 이메일 인증 코드 요청: email={}", maskEmail(request.email()));
        signupService.sendEmailVerify(request.email());
        return ResponseEntity.noContent().build();
    }

    /** 이메일 인증 코드 확인 → 검증된 토큰 반환 */
    @PostMapping("/signup/verify-code")
    @ResponseBody
    public Map<String, Object> verifyCode(@RequestBody EmailVerifyConfirmRequest request) {
        log.info("[Signup] 이메일 인증 코드 확인: email={}", maskEmail(request.email()));
        String verificationToken = signupService.confirmEmailVerify(request.email(), request.code());
        if (verificationToken == null) {
            return Map.of("ok", false, "message", "인증코드가 올바르지 않습니다.");
        }
        return Map.of("ok", true, "verificationToken", verificationToken);
    }

    /** 회원가입 */
    @PostMapping("/signup/submit")
    @ResponseBody
    public ResponseEntity<UserSignupResponse> signup(@RequestBody UserSignupRequest request) {
        log.info("[Signup] 회원가입 요청: email={}", maskEmail(request.email()));
        UserSignupResponse response = signupService.signup(
                request.email(), request.password(), request.userName(),
                request.phoneNumber(), request.token());
        log.info("[Signup] 회원가입 성공: email={}", maskEmail(request.email()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ================================================================
    // 아이디(이메일) 찾기 — SignupService를 통해 auth로 위임
    // ================================================================

    /** 이메일 찾기 */
    @PostMapping("/find-email")
    @ResponseBody
    public Map<String, Object> findId(@RequestBody FindEmailRequest request) {
        log.info("[FindId] 이메일 찾기 요청: userName={}", request.userName());
        String maskedEmail = signupService.findEmail(request.userName(), request.phoneNumber());
        return Map.of("maskedEmail", maskedEmail);
    }

    // ================================================================
    // 비밀번호 재설정 — SignupService를 통해 auth로 위임
    // ================================================================

    /** 비밀번호 재설정 요청 */
    @PostMapping("/reset-password/request")
    @ResponseBody
    public ResponseEntity<Void> requestReset(@RequestBody PasswordResetRequest request) {
        log.info("[ResetPassword] 재설정 요청: email={}", maskEmail(request.email()));
        signupService.requestPasswordReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reset-password/confirm")
    public String resetPasswordConfirmForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        return "reset-password-confirm";
    }

    /** 비밀번호 재설정 확인 */
    @PostMapping("/reset-password/confirm/submit")
    @ResponseBody
    public ResponseEntity<Void> resetPasswordConfirm(@RequestBody PasswordResetConfirmRequest request) {
        log.info("[ResetPassword] 재설정 확인 요청");
        signupService.confirmPasswordReset(request.token(), request.password());
        return ResponseEntity.ok().build();
    }

    // ================================================================
    // 내 정보 (아직 세션 기반 목업)
    // ================================================================

    @GetMapping("/mypage")
    public String myPage(Model model) {
        log.info("[Mypage] 내 정보 조회 요청");
        var info = mypageService.findMyInfo();
        model.addAttribute("email", info.email());
        model.addAttribute("name", info.userName());
        model.addAttribute("phone", info.phoneNumber());
        model.addAttribute("linkedProviders", mypageService.findMyOauths());
        return "mypage";
    }

    @PostMapping("/mypage/update")
    public String updateProfile(@RequestParam String name, @RequestParam String phone) {
        log.info("[Mypage] 정보 수정 요청");
        mypageService.updateMyInfo(name, phone);
        return "redirect:/mypage";
    }

    @PostMapping("/mypage/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 RedirectAttributes redirectAttributes) {
        try {
            mypageService.changePassword(currentPassword, newPassword);
            redirectAttributes.addFlashAttribute("passwordSuccess", "비밀번호를 바꿨어요.");
        } catch (FeignException e) {
            redirectAttributes.addFlashAttribute("passwordError", "현재 비밀번호가 올바르지 않아요.");
        }
        return "redirect:/mypage";
    }

    // ================================================================
    // 공용 헬퍼
    // ================================================================

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
}
