package com.nhnacademy.insightonfront.controller.auth;

import com.nhnacademy.insightonfront.adapter.auth.auth.dto.LoginResult;
import com.nhnacademy.insightonfront.adapter.auth.signup.dto.UserSignupResponse;
import com.nhnacademy.insightonfront.domain.auth.AuthService;
import com.nhnacademy.insightonfront.domain.mypage.MypageService;
import com.nhnacademy.insightonfront.domain.mypage.dto.MyInfoResponse;
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
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

    private final AuthService authService;
    private final SignupService signupService;
    private final MypageService mypageService;

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
                        Model model) {
        try {
            LoginResult result = authService.login(email, password);

            if (result.isPendingRestore()) {
                log.info("[Auth] 탈퇴 복구 대기 계정 로그인 시도");
                model.addAttribute("loginError",
                        "탈퇴 후 복구 가능 기간 내 계정이에요. 계정 복구 후 로그인할 수 있어요.");
                model.addAttribute("pendingRestoreEmail", email);
                return "login";
            }

            writeLoginCookies(result, servletRequest.isSecure(), servletResponse);
            return "redirect:/";

        } catch (FeignException e) {
            log.warn("[Auth] 로그인 처리 중 FeignException: status={}", e.status(), e);
            int status = e.status();
            model.addAttribute("loginError",
                    status <= 0 ? "로그인 서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요." : loginErrorMessage(status));
            return "login";
        } catch (RuntimeException e) {
            log.warn("[Auth] 로그인 처리 중 예외 발생", e);
            model.addAttribute("loginError", "로그인 서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.");
            return "login";
        }
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
        if (accessToken != null && userId != null) {
            authService.logout();
        }

        boolean secure = servletRequest.isSecure();
        expireCookie(servletResponse, "accessToken", secure);
        expireCookie(servletResponse, "refreshToken", secure);
        expireCookie(servletResponse, "userId", secure);
        expireCookie(servletResponse, "userName", secure);
        expireCookie(servletResponse, "groupId", secure);

        return "redirect:/";
    }

    /** 로그인/재활성화 성공 시 공통 쿠키 세팅 (accessToken/refreshToken + userId/userName/groupId). */
    private void writeLoginCookies(LoginResult result, boolean secure, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from("accessToken", result.accessToken())
                        .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                        .maxAge(Duration.ofMinutes(15))
                        .build().toString());

        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from("refreshToken", result.refreshToken())
                        .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                        .maxAge(Duration.ofDays(15))
                        .build().toString());

        if (result.userId() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from("userId", result.userId().toString())
                            .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                            .maxAge(Duration.ofDays(15))
                            .build().toString());
        }

        if (result.userName() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from("userName",
                                    URLEncoder.encode(result.userName(), StandardCharsets.UTF_8))
                            .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                            .maxAge(Duration.ofDays(15))
                            .build().toString());
        }

        if (result.groupId() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from("groupId", result.groupId().toString())
                            .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                            .maxAge(Duration.ofDays(15))
                            .build().toString());
        }
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

    /**
     * 이메일 중복 확인
     */
    @PostMapping("/signup/check-email")
    @ResponseBody
    public Map<String, Object> checkEmailDuplicate(@RequestBody EmailAvailableRequest request) {
        boolean available = signupService.checkEmailAvailable(request.email());
        return Map.of("available", available);
    }

    /**
     * 이메일 인증 코드 요청
     */
    @PostMapping("/signup/send-code")
    @ResponseBody
    public ResponseEntity<Void> sendVerificationCode(@RequestBody EmailVerifyRequest request) {
        signupService.sendEmailVerify(request.email());
        return ResponseEntity.noContent().build();
    }

    /**
     * 이메일 인증 코드 확인 → 검증된 토큰 반환
     */
    @PostMapping("/signup/verify-code")
    @ResponseBody
    public Map<String, Object> verifyCode(@RequestBody EmailVerifyConfirmRequest request) {
        String verificationToken = signupService.confirmEmailVerify(request.email(), request.code());
        if (verificationToken == null) {
            return Map.of("ok", false, "message", "인증코드가 올바르지 않습니다.");
        }
        return Map.of("ok", true, "verificationToken", verificationToken);
    }

    /**
     * 회원가입
     */
    @PostMapping("/signup/submit")
    @ResponseBody
    public ResponseEntity<?> signup(@RequestBody UserSignupRequest request) {
        try {
            UserSignupResponse response = signupService.signup(
                    request.email(), request.password(), request.userName(),
                    request.phoneNumber(), request.token());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (FeignException e) {
            log.warn("[Auth] 회원가입 처리 중 FeignException: status={}", e.status());
            return ResponseEntity.status(e.status() > 0 ? e.status() : 500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.contentUTF8());
        }
    }

    // ================================================================
    // 아이디(이메일) 찾기 — SignupService를 통해 auth로 위임
    // ================================================================

    /**
     * 이메일 찾기
     */
    @PostMapping("/find-email")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> findId(@RequestBody FindEmailRequest request) {
        try {
            String maskedEmail = authService.findEmail(request.userName(), request.phoneNumber());
            return ResponseEntity.ok(Map.of("maskedEmail", maskedEmail));
        } catch (FeignException.NotFound e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();   // 404 그대로 전달
        }
    }

    // ================================================================
    // 비밀번호 재설정 — SignupService를 통해 auth로 위임
    // ================================================================

    /**
     * 비밀번호 재설정 요청
     */
    @PostMapping("/password/reset-request")
    @ResponseBody
    public ResponseEntity<Void> requestReset(@RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/password/reset")
    public String resetPasswordConfirmForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        return "reset-password";
    }

    /**
     * 비밀번호 재설정 확인
     */
    @PostMapping("/password/reset-confirm")
    public String resetPasswordConfirm(@RequestParam String password,
                                       @RequestParam String token,
                                       Model model) {
        try {
            authService.confirmPasswordReset(token, password);
            model.addAttribute("done", true);      // HTML의 th:if="${done}" 완료화면
            return "reset-password";
        } catch (FeignException.BadRequest e) {
            // auth가 "이전과 동일한 비번" 등 400
            model.addAttribute("token", token);
            model.addAttribute("resetError", "새 비밀번호는 이전 비밀번호와 달라야 해요.");
            return "reset-password";
        } catch (FeignException e) {
            // 토큰 만료/무효 등
            model.addAttribute("token", token);
            model.addAttribute("resetError", "링크가 만료됐거나 유효하지 않아요. 재설정 링크를 다시 요청해주세요.");
            return "reset-password";
        }
    }

    // ================================================================
    // 탈퇴 계정 재활성화(복구) — AuthService를 통해 auth로 위임
    //   이메일 인증 코드 → 복구 + 즉시 로그인 (응답 규약은 /login 과 동일)
    // ================================================================

    @GetMapping("/reactivate")
    public String reactivateForm(@RequestParam(required = false) String email, Model model) {
        model.addAttribute("email", email);
        return "reactivate";
    }

    /**
     * 재활성화 인증 코드 발송. 계정 존재 여부는 노출하지 않고, 쿨다운/재전송 초과만 4xx로 구분한다.
     */
    @PostMapping("/reactivate/send-code")
    @ResponseBody
    public ResponseEntity<Void> sendReactivateCode(@RequestBody EmailVerifyRequest request) {
        try {
            authService.requestReactivateEmailVerify(request.email());
            return ResponseEntity.noContent().build();
        } catch (FeignException e) {
            log.warn("[Auth] 재활성화 코드 발송 실패: status={}", e.status());
            int status = e.status();               // 429 쿨다운 / 423 재전송 초과 잠금 / 그 외
            return ResponseEntity.status(status >= 400 && status < 500 ? status : 502).build();
        }
    }

    /**
     * 재활성화 인증 코드 확인 → 성공 시 계정 복구 + 로그인 쿠키 세팅 후 홈으로.
     */
    @PostMapping("/reactivate/confirm")
    public String reactivateConfirm(@RequestParam String email,
                                    @RequestParam String code,
                                    HttpServletRequest servletRequest,
                                    HttpServletResponse servletResponse,
                                    Model model) {
        try {
            LoginResult result = authService.reactivateConfirm(email, code);
            writeLoginCookies(result, servletRequest.isSecure(), servletResponse);
            return "redirect:/";
        } catch (FeignException e) {
            log.warn("[Auth] 재활성화 확인 실패: status={}", e.status());
            model.addAttribute("email", email);
            model.addAttribute("reactivateError", reactivateErrorMessage(e.status()));
            return "reactivate";
        } catch (RuntimeException e) {
            log.warn("[Auth] 재활성화 확인 중 예외", e);
            model.addAttribute("email", email);
            model.addAttribute("reactivateError", "복구 서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.");
            return "reactivate";
        }
    }

    private String reactivateErrorMessage(int status) {
        return switch (status) {
            case 400 -> "인증 코드가 올바르지 않거나 만료됐어요.";
            case 423 -> "인증 시도가 초과되어 잠시 잠겼어요. 잠시 후 다시 시도해주세요.";
            case 404, 409 -> "복구할 수 있는 계정을 찾지 못했어요.";
            default -> "복구에 실패했어요. 잠시 후 다시 시도해주세요.";
        };
    }

    // ================================================================
    // 내 정보 조회
    // ================================================================

    @GetMapping("/mypage")
    public String myPage(Model model) {
        MyInfoResponse info = mypageService.findMyInfo();
        model.addAttribute("email", info.email());
        model.addAttribute("name", info.userName());
        model.addAttribute("phone", info.phoneNumber());
        model.addAttribute("groupName", info.groupName());
        model.addAttribute("oauths", mypageService.findMyOauths());
        return "mypage";
    }

    // ================================================================
    // 내 정보 수정
    // ================================================================

    @GetMapping("/mypage/edit")
    public String myPageEditForm(Model model) {
        MyInfoResponse info = mypageService.findMyInfo();
        model.addAttribute("email", info.email());
        model.addAttribute("name", info.userName());
        model.addAttribute("phone", info.phoneNumber());
        return "mypage/edit";
    }

    @PostMapping("/mypage/edit")
    public String updateProfile(@RequestParam String name, @RequestParam String phone,
                                RedirectAttributes ra) {
        try {
            mypageService.updateMyInfo(name, phone);
            ra.addFlashAttribute("profileSuccess", "정보를 수정했어요.");
        } catch (FeignException.BadRequest e) {
            // 400 - 전화번호 형식 오류
            log.warn("[MyPage] 비밀번호 변경 실패(400) - 전화번호 형식에 맞지 않음");
            ra.addFlashAttribute("profileError", "전화번호 형식이 맞지 않습니다.");
            return "redirect:/mypage/edit";
        }
        return "redirect:/mypage";
    }

    // ================================================================
    // 비밀번호 재설정
    // ================================================================

    @GetMapping("/mypage/password")
    public String passwordResetForm() {
        return "mypage/password";
    }

    @PostMapping("/mypage/password")
    public String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
                                 RedirectAttributes ra) {
        try {
            mypageService.changePassword(currentPassword, newPassword);
            ra.addFlashAttribute("passwordSuccess", "비밀번호가 변경되었습니다.");
            return "redirect:/mypage";
        } catch (FeignException.Unauthorized e) {
            // 401 - 현재 비밀번호 불일치
            log.warn("[MyPage] 비밀번호 변경 실패(401) - 현재 비밀번호 불일치");
            ra.addFlashAttribute("passwordError", "현재 비밀번호가 올바르지 않습니다.");
            return "redirect:/mypage/password";

        } catch (FeignException.BadRequest e) {
            // 400 - 새 비밀번호가 기존과 동일
            log.warn("[MyPage] 비밀번호 변경 실패(400) - 새 비밀번호 동일");
            ra.addFlashAttribute("passwordError", "새 비밀번호는 현재 비밀번호와 동일하지 않아야 합니다.");
            return "redirect:/mypage/password";
        } catch (FeignException e) {
            log.warn("[MyPage] 비밀번호 변경 실패: status={}", e.status());
            ra.addFlashAttribute("passwordError", "비밀번호 변경에 실패했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/mypage/password";
        }
    }

    // ================================================================
    // 소셜 계정 연동 해제
    // ================================================================

    @PostMapping("/mypage/social/unlink")
    @ResponseBody
    public ResponseEntity<Void> unlinkSocial(@RequestParam Long oauthId) {
        mypageService.unlinkOauth(oauthId);
        return ResponseEntity.noContent().build();
    }

    // ================================================================
    // 회원 탈퇴
    // ================================================================

    @PostMapping("/mypage/withdraw")
    @ResponseBody
    public ResponseEntity<Void> withdraw(HttpServletRequest req, HttpServletResponse res) {
        mypageService.withdraw();
        boolean secure = req.isSecure();
        expireCookie(res, "accessToken", secure);
        expireCookie(res, "refreshToken", secure);
        expireCookie(res, "userId", secure);
        expireCookie(res, "userName", secure);
        expireCookie(res, "groupId", secure);
        return ResponseEntity.noContent().build();
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
