package com.nhnacademy.insightonfront.controller.auth;

import com.nhnacademy.insightonfront.adapter.admin.dto.AdminFindUsersResponse;
import com.nhnacademy.insightonfront.adapter.admin.dto.AdminUserDetailResponse;
import com.nhnacademy.insightonfront.adapter.admin.dto.RoleResponse;
import com.nhnacademy.insightonfront.adapter.auth.auth.dto.LoginResult;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.domain.admin.AdminService;
import com.nhnacademy.insightonfront.domain.admin.dto.RolesUpdateRequest;
import com.nhnacademy.insightonfront.domain.auth.AuthService;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 관리자 회원관리 컨트롤러.
 * 흐름: 화면(HTML) → JS(fetch) → 이 컨트롤러 → AdminService → (Feign) auth
 *
 * - 페이지 렌더링: GET /admin/users, /admin/users/{userId}
 * - 액션 API(JS fetch용): 목록/상세 JSON 조회 + 차단/휴면/활성화/권한변경/강제로그아웃
 *
 *    접근 제어: 이 경로(/admin/**)는 ADMIN 권한만 접근해야 한다.
 *    게이트웨이 role 검사 또는 프론트 인터셉터로 보호할 것.
 */
@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AuthAdminController {

    private final AuthService authService;
    private final AdminService adminService;

    // ================================================================
    // Admin 메인 페이지
    // ================================================================
    /** 관리자 메인 */
    @GetMapping("/main")
    public String main(@CookieValue(value = "accessToken", required = false) String accessToken,
                       @CookieValue(value = "userName", required = false) String userName,
                       Model model) {
        // 로그인 안 됨 → 로그인 페이지로
        if (accessToken == null) {
            return "redirect:/admin/login";
        }
        // userName 표시 (로그인 때 URL 인코딩했으니 디코드)
        if (userName != null) {
            model.addAttribute("userName", URLDecoder.decode(userName, StandardCharsets.UTF_8));
        }
        return "admin/main";
    }

    // ================================================================
    // 로그인
    // ================================================================

    /** 관리자 로그인 페이지 */
    @GetMapping("/login")
    public String loginForm() {
        return "admin/login";
    }

    /** 관리자 로그인 처리 */
    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        HttpServletRequest servletRequest,
                        HttpServletResponse servletResponse,
                        Model model) {

        try {
            LoginResult result = authService.loginAdmin(email, password);

            if (result.isPendingRestore()) {
                log.info("[Auth] 탈퇴 복구 대기 계정(관리자) 로그인 시도");
                model.addAttribute("loginError",
                        "탈퇴 후 복구 가능 기간 내 계정이에요. 계정을 복구한 뒤 다시 로그인해 주세요.");
                return "admin/login";
            }

            boolean secure = servletRequest.isSecure();

            servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from("accessToken", result.accessToken())
                            .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                            .maxAge(Duration.ofMinutes(15))
                            .build().toString());

            servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from("refreshToken", result.refreshToken())
                            .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                            .maxAge(Duration.ofDays(15))
                            .build().toString());

            if (result.userId() != null) {
                servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                        ResponseCookie.from("userId", result.userId().toString())
                                .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                                .maxAge(Duration.ofDays(15))
                                .build().toString());
            }

            if (result.userName() != null) {
                servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                        ResponseCookie.from("userName",
                                        URLEncoder.encode(result.userName(), StandardCharsets.UTF_8))
                                .httpOnly(true)
                                .secure(secure)
                                .path("/")
                                .sameSite("Lax")
                                .maxAge(Duration.ofDays(15))
                                .build().toString());
            }

            return "redirect:/admin/main";

        } catch (FeignException e) {
            log.warn("[Auth] 로그인 처리 중 FeignException: status={}", e.status(), e);
            int status = e.status();
            model.addAttribute("loginError",
                    status <= 0 ? "로그인 서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요." : loginErrorMessage(status));
            return "admin/login";
        } catch (RuntimeException e) {
            log.warn("[Auth] 로그인 처리 중 예외 발생", e);
            model.addAttribute("loginError", "로그인 서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.");
            return "admin/login";
        }
    }

    private String loginErrorMessage(int status) {
        return switch (status) {
            case 401, 400 -> "이메일 또는 비밀번호가 올바르지 않아요.";
            case 403 -> "이용이 제한된(정지) 계정이에요. 관리자에게 문의해주세요.";
            case 423 -> "로그인이 일시적으로 잠겼어요. 잠시 후 다시 시도해주세요.";
            default -> "로그인에 실패했어요. 잠시 후 다시 시도해주세요.";
        };
    }

    // 로그아웃
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
        return "redirect:/admin/login";
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


    // ================================================================
    // 페이지 렌더링
    // ================================================================

    /** 회원 관리 목록 페이지 */
    @GetMapping("/users")
    public String usersPage(@CookieValue(value = "userName", required = false) String userName,
                            Model model) {
        addUserName(userName, model);
        return "admin/users";   // templates/admin/users.html
    }

    /** 회원 상세 페이지 */
    @GetMapping("/users/{userId}")
    public String userDetailPage(@PathVariable Long userId,
                                 @CookieValue(value = "userName", required = false) String userName,
                                 Model model) {
        addUserName(userName, model);
        model.addAttribute("userId", userId);
        return "admin/user-detail";   // templates/admin/user-detail.html
    }

    /** 로그인 때 URL 인코딩해 담아둔 userName 쿠키를 디코드해 모델에 넣는다 (헤더 인사말용). */
    private void addUserName(String userName, Model model) {
        if (userName != null) {
            model.addAttribute("userName", URLDecoder.decode(userName, StandardCharsets.UTF_8));
        }
    }

    // ================================================================
    // 액션 API (JS fetch로 호출) — JSON 응답
    // ================================================================

    /** 회원 목록 조회 (검색·페이징) */
    @GetMapping("/api/users")
    @ResponseBody
    public ResponseEntity<PageResponse<AdminFindUsersResponse>> findUsers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<AdminFindUsersResponse> users =
                adminService.findUsers(email, userName, status, page, size);
        return ResponseEntity.ok(users);
    }

    /** 회원 상세 조회 */
    @GetMapping("/api/users/{userId}")
    @ResponseBody
    public ResponseEntity<AdminUserDetailResponse> findUserDetail(@PathVariable Long userId) {
        return ResponseEntity.ok(adminService.findUserDetail(userId));
    }

    /** 지정 가능한 권한 목록 */
    @GetMapping("/api/roles")
    @ResponseBody
    public ResponseEntity<List<RoleResponse>> findRoles() {
        return ResponseEntity.ok(adminService.findRoles());
    }

    /** 회원 계정 차단 */
    @PostMapping("/api/users/{userId}/block")
    @ResponseBody
    public ResponseEntity<Void> block(@PathVariable Long userId) {
        adminService.block(userId);
        return ResponseEntity.noContent().build();
    }

    /** 회원 계정 휴면 전환 */
    @PostMapping("/api/users/{userId}/sleep")
    @ResponseBody
    public ResponseEntity<Void> sleep(@PathVariable Long userId) {
        adminService.sleep(userId);
        return ResponseEntity.noContent().build();
    }

    /** 회원 계정 활성화 (복구) */
    @PostMapping("/api/users/{userId}/activate")
    @ResponseBody
    public ResponseEntity<Void> activate(@PathVariable Long userId) {
        adminService.activate(userId);
        return ResponseEntity.noContent().build();
    }

    /** 회원 권한 전체 교체 (roles 목록이 최종 상태) */
    @PutMapping("/api/users/{userId}/roles")
    @ResponseBody
    public ResponseEntity<Void> updateRoles(@PathVariable Long userId,
                                            @RequestBody RolesUpdateRequest request) {
        adminService.updateRoles(userId, request.roles());
        return ResponseEntity.ok().build();
    }

    /** 강제 로그아웃 */
    @PostMapping("/api/users/{userId}/force-logout")
    @ResponseBody
    public ResponseEntity<Void> forceLogout(@PathVariable Long userId) {
        adminService.forceLogout(userId);
        return ResponseEntity.noContent().build();
    }
}