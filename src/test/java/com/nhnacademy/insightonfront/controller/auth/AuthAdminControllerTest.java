package com.nhnacademy.insightonfront.controller.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.nhnacademy.insightonfront.adapter.auth.auth.dto.LoginResult;
import com.nhnacademy.insightonfront.domain.admin.AdminService;
import com.nhnacademy.insightonfront.domain.auth.AuthService;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import jakarta.servlet.http.Cookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * AuthAdminController HTTP 계약 테스트 — 관리자 메인 접근·로그인·로그아웃·회원관리 화면과
 * 액션 API 의 뷰/리다이렉트/상태코드를 standalone MockMvc 로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthAdminControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private AdminService adminService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new AuthAdminController(authService, adminService, new ObjectMapper()))
                .setViewResolvers(new InternalResourceViewResolver("/WEB-INF/views/", ".jsp"))
                .build();
    }

    private static FeignException feignStatus(int status) {
        Request request = Request.create(Request.HttpMethod.POST, "http://localhost/api/v1/admin/login",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        Response response = Response.builder()
                .status(status).reason("test").request(request).headers(Map.of()).build();
        return FeignException.errorStatus("AdminClient#login()", response);
    }

    private static FeignException feignStatusWithMessage(int status, String message) {
        Request request = Request.create(Request.HttpMethod.POST, "http://localhost/api/v1/admin/users/1/block",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        byte[] body = ("{\"status\":" + status + ",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        Response response = Response.builder()
                .status(status).reason("test").request(request).headers(Map.of()).body(body).build();
        return FeignException.errorStatus("AdminClient#block()", response);
    }

    // ================================================================
    // GET /admin/main
    // ================================================================

    @Test
    @DisplayName("GET /admin/main — accessToken 없으면 /admin/login 으로 리다이렉트")
    void main_noAccessToken_redirectsToLogin() throws Exception {
        mvc.perform(get("/admin/main"))
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    @DisplayName("GET /admin/main — 로그인 상태면 admin/main 뷰 + userName 쿠키를 디코드해 모델에 넣는다")
    void main_withAccessToken_returnsMainViewWithDecodedName() throws Exception {
        String encoded = URLEncoder.encode("admin kim", StandardCharsets.UTF_8); // "admin+kim"

        mvc.perform(get("/admin/main")
                        .cookie(new Cookie("accessToken", "AT"), new Cookie("userName", encoded)))
                .andExpect(view().name("admin/main"))
                .andExpect(model().attribute("userName", "admin kim"));
    }

    // ================================================================
    // POST /admin/login
    // ================================================================

    @Test
    @DisplayName("POST /admin/login — 성공 → /admin/main 리다이렉트")
    void login_success_redirectsToMain() throws Exception {
        when(authService.loginAdmin("admin@test.com", "pw"))
                .thenReturn(LoginResult.success(9L, "관리자", null, "AT", "RT"));

        mvc.perform(post("/admin/login").param("email", "admin@test.com").param("password", "pw"))
                .andExpect(redirectedUrl("/admin/main"));
    }

    @Test
    @DisplayName("POST /admin/login — 복구 대기 계정 → admin/login 뷰 + 안내 메시지")
    void login_pendingRestore_returnsLoginView() throws Exception {
        when(authService.loginAdmin(any(), any())).thenReturn(LoginResult.pendingRestore("RESTORE"));

        mvc.perform(post("/admin/login").param("email", "gone@test.com").param("password", "pw"))
                .andExpect(view().name("admin/login"))
                .andExpect(model().attributeExists("loginError"));
    }

    @Test
    @DisplayName("POST /admin/login — 401 → admin/login 뷰 + 자격 오류 메시지")
    void login_unauthorized_returnsLoginView() throws Exception {
        when(authService.loginAdmin(any(), any())).thenThrow(feignStatus(401));

        mvc.perform(post("/admin/login").param("email", "admin@test.com").param("password", "wrong"))
                .andExpect(view().name("admin/login"))
                .andExpect(model().attribute("loginError",
                        org.hamcrest.Matchers.containsString("이메일 또는 비밀번호")));
    }

    // ================================================================
    // POST /admin/logout
    // ================================================================

    @Test
    @DisplayName("POST /admin/logout — accessToken+userId 있으면 auth 로그아웃 후 /admin/login 리다이렉트")
    void logout_withTokens_callsLogout() throws Exception {
        mvc.perform(post("/admin/logout")
                        .cookie(new Cookie("accessToken", "AT"), new Cookie("userId", "9")))
                .andExpect(redirectedUrl("/admin/login"));

        verify(authService).logout();
    }

    @Test
    @DisplayName("POST /admin/logout — 토큰 없으면 auth 로그아웃 호출하지 않는다")
    void logout_withoutTokens_skipsLogout() throws Exception {
        mvc.perform(post("/admin/logout"))
                .andExpect(redirectedUrl("/admin/login"));

        verify(authService, never()).logout();
    }

    // ================================================================
    // 페이지 렌더링
    // ================================================================

    @Test
    @DisplayName("GET /admin/users — admin/users 뷰")
    void usersPage_returnsView() throws Exception {
        mvc.perform(get("/admin/users"))
                .andExpect(view().name("admin/users"));
    }

    @Test
    @DisplayName("GET /admin/users/{userId} — admin/user-detail 뷰 + userId 모델")
    void userDetailPage_returnsViewWithUserId() throws Exception {
        mvc.perform(get("/admin/users/5"))
                .andExpect(view().name("admin/user-detail"))
                .andExpect(model().attribute("userId", 5L));
    }

    // ================================================================
    // 액션 API
    // ================================================================

    @Test
    @DisplayName("POST /admin/api/users/{userId}/block — 204 + adminService.block 호출")
    void block_returns204() throws Exception {
        mvc.perform(post("/admin/api/users/5/block"))
                .andExpect(status().isNoContent());

        verify(adminService).block(5L);
    }

    @Test
    @DisplayName("POST /admin/api/users/{userId}/activate — 204 + adminService.activate 호출")
    void activate_returns204() throws Exception {
        mvc.perform(post("/admin/api/users/5/activate"))
                .andExpect(status().isNoContent());

        verify(adminService).activate(5L);
    }

    @Test
    @DisplayName("POST /admin/api/users/{userId}/block — auth가 403+메시지로 거부하면 그 메시지를 그대로 JSON으로 반환")
    void block_selfTarget_relaysDownstreamMessage() throws Exception {
        org.mockito.Mockito.doThrow(feignStatusWithMessage(403, "자기 자신을 차단할 수 없습니다."))
                .when(adminService).block(1L);

        mvc.perform(post("/admin/api/users/1/block"))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.message").value("자기 자신을 차단할 수 없습니다."));
    }
}
