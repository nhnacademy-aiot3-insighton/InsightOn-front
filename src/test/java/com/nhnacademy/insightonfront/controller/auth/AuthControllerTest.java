package com.nhnacademy.insightonfront.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.nhnacademy.insightonfront.domain.auth.AuthService;
import com.nhnacademy.insightonfront.domain.mypage.MypageService;
import com.nhnacademy.insightonfront.domain.mypage.dto.MyInfoResponse;
import com.nhnacademy.insightonfront.domain.signup.SignupService;
import com.nhnacademy.insightonfront.support.TestJwt;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * AuthController HTTP 계약 테스트 — 로그인/로그아웃/소셜 로그인/재활성화/마이페이지 진입의
 * 뷰 이름·리다이렉트·모델 속성·쿠키를 standalone MockMvc 로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private SignupService signupService;
    @Mock
    private MypageService mypageService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService, signupService, mypageService, new ObjectMapper());
        ReflectionTestUtils.setField(controller, "oauthAuthBaseUrl", "");
        // 뷰 이름과 요청 경로가 같아(예: /login → "login") 발생하는 Circular view path 를 피하려고
        // 실제로 렌더링하지 않는 prefix 付き ViewResolver 를 둔다. view().name() 검증에는 영향 없음.
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setViewResolvers(new InternalResourceViewResolver("/WEB-INF/views/", ".jsp"))
                .build();
    }

    private static FeignException feignStatus(int status) {
        Request request = Request.create(Request.HttpMethod.POST, "http://localhost/api/v1/auth/login",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        Response response = Response.builder()
                .status(status).reason("test").request(request).headers(Map.of()).build();
        return FeignException.errorStatus("AuthClient#login()", response);
    }

    private static List<String> setCookies(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    }

    // ================================================================
    // POST /login
    // ================================================================

    @Test
    @DisplayName("POST /login — 성공 & 그룹 보유 → /my-group 리다이렉트 + 로그인 쿠키")
    void login_successWithGroup_redirectsToDashboard() throws Exception {
        when(authService.login("user@test.com", "pw"))
                .thenReturn(LoginResult.success(1L, "홍길동", 7L, "AT", "RT"));

        MvcResult result = mvc.perform(post("/login")
                        .param("email", "user@test.com")
                        .param("password", "pw"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-group"))
                .andReturn();

        assertThat(setCookies(result)).anyMatch(c -> c.startsWith("accessToken=AT"));
        assertThat(setCookies(result)).anyMatch(c -> c.startsWith("refreshToken=RT"));
    }

    @Test
    @DisplayName("POST /login — 성공 & 그룹 없음 → / 리다이렉트")
    void login_successNoGroup_redirectsToRoot() throws Exception {
        when(authService.login("user@test.com", "pw"))
                .thenReturn(LoginResult.success(1L, "홍길동", null, "AT", "RT"));

        mvc.perform(post("/login").param("email", "user@test.com").param("password", "pw"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("POST /login — 복구 대기 계정 → login 뷰 + 안내 메시지")
    void login_pendingRestore_returnsLoginViewWithMessage() throws Exception {
        when(authService.login("gone@test.com", "pw"))
                .thenReturn(LoginResult.pendingRestore("RESTORE"));

        mvc.perform(post("/login").param("email", "gone@test.com").param("password", "pw"))
                .andExpect(view().name("login"))
                .andExpect(model().attributeExists("loginError"))
                .andExpect(model().attribute("pendingRestoreEmail", "gone@test.com"));
    }

    @Test
    @DisplayName("POST /login — 401 → login 뷰 + '이메일 또는 비밀번호' 메시지")
    void login_unauthorized_returnsLoginViewWithCredentialMessage() throws Exception {
        when(authService.login(any(), any())).thenThrow(feignStatus(401));

        mvc.perform(post("/login").param("email", "user@test.com").param("password", "wrong"))
                .andExpect(view().name("login"))
                .andExpect(model().attribute("loginError",
                        org.hamcrest.Matchers.containsString("이메일 또는 비밀번호")));
    }

    @Test
    @DisplayName("POST /login — 423 → login 뷰 + 잠금 메시지")
    void login_locked_returnsLoginViewWithLockMessage() throws Exception {
        when(authService.login(any(), any())).thenThrow(feignStatus(423));

        mvc.perform(post("/login").param("email", "user@test.com").param("password", "wrong"))
                .andExpect(view().name("login"))
                .andExpect(model().attribute("loginError", org.hamcrest.Matchers.containsString("잠겼")));
    }

    @Test
    @DisplayName("POST /login — 연결 불가(status<=0) → login 뷰 + 연결 실패 메시지")
    void login_connectionFailure_returnsLoginViewWithConnectMessage() throws Exception {
        when(authService.login(any(), any())).thenThrow(feignStatus(-1));

        mvc.perform(post("/login").param("email", "user@test.com").param("password", "pw"))
                .andExpect(view().name("login"))
                .andExpect(model().attribute("loginError",
                        org.hamcrest.Matchers.containsString("연결할 수 없어요")));
    }

    // ================================================================
    // POST /logout
    // ================================================================

    @Test
    @DisplayName("POST /logout — accessToken+userId 있으면 auth 로그아웃 호출 후 / 리다이렉트 + 쿠키 만료")
    void logout_withTokens_callsLogoutAndExpiresCookies() throws Exception {
        MvcResult result = mvc.perform(post("/logout")
                        .cookie(new Cookie("accessToken", "AT"), new Cookie("userId", "1")))
                .andExpect(redirectedUrl("/"))
                .andReturn();

        verify(authService).logout();
        assertThat(setCookies(result)).anyMatch(c -> c.startsWith("accessToken=") && c.contains("Max-Age=0"));
    }

    @Test
    @DisplayName("POST /logout — 토큰 쿠키 없으면 auth 로그아웃 호출하지 않고 / 리다이렉트")
    void logout_withoutTokens_skipsLogout() throws Exception {
        mvc.perform(post("/logout"))
                .andExpect(redirectedUrl("/"));

        verify(authService, never()).logout();
    }

    // ================================================================
    // 소셜 로그인
    // ================================================================

    @Test
    @DisplayName("GET /oauth/authorize/{provider} — 지원 provider → auth authorize 로 리다이렉트")
    void oauthAuthorize_validProvider_redirectsToAuth() throws Exception {
        mvc.perform(get("/oauth/authorize/google"))
                .andExpect(redirectedUrl("/api/v1/auth/oauth/authorize/google"));
    }

    @Test
    @DisplayName("GET /oauth/authorize/{provider} — 미지원 provider → /login?oauthError=1")
    void oauthAuthorize_invalidProvider_redirectsToLoginError() throws Exception {
        mvc.perform(get("/oauth/authorize/kakao"))
                .andExpect(redirectedUrl("/login?oauthError=1"));
    }

    @Test
    @DisplayName("GET /oauth/link/{provider} — mergeWith 파라미터를 auth 로 전달")
    void oauthLink_withMergeWith_passesThrough() throws Exception {
        mvc.perform(get("/oauth/link/github").param("mergeWith", "5"))
                .andExpect(redirectedUrl("/api/v1/auth/oauth/link/authorize/github?mergeWith=5"));
    }

    @Test
    @DisplayName("GET /oauth/complete — accessToken 쿠키 없으면 /login?oauthError=1")
    void oauthComplete_noAccessToken_redirectsToLoginError() throws Exception {
        mvc.perform(get("/oauth/complete"))
                .andExpect(redirectedUrl("/login?oauthError=1"));
    }

    @Test
    @DisplayName("GET /oauth/complete — 성공 & 그룹 보유 → /my-group")
    void oauthComplete_success_redirectsByGroup() throws Exception {
        when(authService.hydrateFromAccessToken("AT", "RT"))
                .thenReturn(LoginResult.success(1L, "홍길동", 7L, "AT", "RT"));

        mvc.perform(get("/oauth/complete")
                        .cookie(new Cookie("accessToken", "AT"), new Cookie("refreshToken", "RT")))
                .andExpect(redirectedUrl("/my-group"));
    }

    @Test
    @DisplayName("GET /oauth/complete — hydrate 중 예외 → /login?oauthError=1")
    void oauthComplete_hydrateThrows_redirectsToLoginError() throws Exception {
        when(authService.hydrateFromAccessToken(any(), any())).thenThrow(new RuntimeException("boom"));

        mvc.perform(get("/oauth/complete").cookie(new Cookie("accessToken", "AT")))
                .andExpect(redirectedUrl("/login?oauthError=1"));
    }

    // ================================================================
    // 재활성화
    // ================================================================

    @Test
    @DisplayName("POST /reactivate/confirm — 성공 → 그룹 유무로 리다이렉트")
    void reactivateConfirm_success_redirects() throws Exception {
        when(authService.reactivateConfirm("gone@test.com", "123456"))
                .thenReturn(LoginResult.success(1L, "복구", null, "AT", "RT"));

        mvc.perform(post("/reactivate/confirm")
                        .param("email", "gone@test.com").param("code", "123456"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("POST /reactivate/confirm — 400 → reactivate 뷰 + 에러 메시지")
    void reactivateConfirm_badRequest_returnsReactivateView() throws Exception {
        when(authService.reactivateConfirm(any(), any())).thenThrow(feignStatus(400));

        mvc.perform(post("/reactivate/confirm")
                        .param("email", "gone@test.com").param("code", "000000"))
                .andExpect(view().name("reactivate"))
                .andExpect(model().attributeExists("reactivateError"))
                .andExpect(model().attribute("email", "gone@test.com"));
    }

    // ================================================================
    // 마이페이지 진입 — brandHref
    // ================================================================

    private void stubMyInfo() {
        when(mypageService.findMyInfo())
                .thenReturn(new MyInfoResponse("u@test.com", "홍길동", "01011112222", null, "우리그룹"));
        when(mypageService.findMyOauths()).thenReturn(List.of());
    }

    @Test
    @DisplayName("GET /mypage — 관리자 토큰이면 brandHref=/admin/main")
    void myPage_adminToken_brandHrefIsAdminMain() throws Exception {
        stubMyInfo();
        String adminToken = TestJwt.withRoles("ADMIN");
        when(authService.hasAdminRole(adminToken)).thenReturn(true);

        mvc.perform(get("/mypage")
                        .cookie(new Cookie("accessToken", adminToken), new Cookie("groupId", "7")))
                .andExpect(view().name("mypage"))
                .andExpect(model().attribute("brandHref", "/admin/main"));
    }

    @Test
    @DisplayName("GET /mypage — 일반 사용자 & 그룹 보유 → brandHref=/my-group")
    void myPage_memberWithGroup_brandHrefIsDashboard() throws Exception {
        stubMyInfo();
        when(authService.hasAdminRole(any())).thenReturn(false);

        mvc.perform(get("/mypage").cookie(new Cookie("groupId", "7")))
                .andExpect(model().attribute("brandHref", "/my-group"));
    }

    @Test
    @DisplayName("GET /mypage — 일반 사용자 & 그룹 없음 → brandHref=/")
    void myPage_memberNoGroup_brandHrefIsRoot() throws Exception {
        stubMyInfo();
        when(authService.hasAdminRole(any())).thenReturn(false);

        mvc.perform(get("/mypage"))
                .andExpect(model().attribute("brandHref", "/"));
    }

    @Test
    @DisplayName("GET /mypage/edit — 관리자 토큰이면 brandHref=/admin/main")
    void myPageEdit_adminToken_brandHrefIsAdminMain() throws Exception {
        when(mypageService.findMyInfo())
                .thenReturn(new MyInfoResponse("u@test.com", "홍길동", "01011112222", null, "우리그룹"));
        String adminToken = TestJwt.withRoles("ADMIN");
        when(authService.hasAdminRole(adminToken)).thenReturn(true);

        mvc.perform(get("/mypage/edit").cookie(new Cookie("accessToken", adminToken)))
                .andExpect(view().name("mypage/edit"))
                .andExpect(model().attribute("brandHref", "/admin/main"));
    }

    @Test
    @DisplayName("GET /mypage/password — 일반 사용자 & 그룹 없음 → brandHref=/")
    void myPagePassword_memberNoGroup_brandHrefIsRoot() throws Exception {
        when(authService.hasAdminRole(any())).thenReturn(false);

        mvc.perform(get("/mypage/password"))
                .andExpect(view().name("mypage/password"))
                .andExpect(model().attribute("brandHref", "/"));
    }
}
