package com.nhnacademy.insightonfront.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonfront.adapter.admin.AdminClient;
import com.nhnacademy.insightonfront.adapter.auth.auth.AuthClient;
import com.nhnacademy.insightonfront.adapter.auth.auth.dto.LoginResult;
import com.nhnacademy.insightonfront.adapter.auth.auth.dto.UserLoginResponse;
import com.nhnacademy.insightonfront.adapter.auth.signup.SignupClient;
import com.nhnacademy.insightonfront.adapter.core.group.GroupClient;
import com.nhnacademy.insightonfront.adapter.core.group.dto.MyGroupIdResponse;
import com.nhnacademy.insightonfront.support.TestJwt;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

/**
 * AuthService 단위 테스트 — auth/admin/group Feign 호출을 목킹하고,
 * 토큰 파싱·groupId 조회·로그인 결과 조립·로그아웃 예외 무시를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthClient authClient;
    @Mock
    private AdminClient adminClient;
    @Mock
    private SignupClient signupClient;
    @Mock
    private GroupClient groupClient;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authClient, adminClient, signupClient, groupClient, new ObjectMapper());
    }

    private static ResponseEntity<UserLoginResponse> loginResponse(UserLoginResponse body, String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        if (refreshToken != null) {
            headers.add(HttpHeaders.SET_COOKIE, "refreshToken=" + refreshToken + "; Path=/; HttpOnly");
        }
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private static FeignException.NotFound feignNotFound() {
        Request request = Request.create(Request.HttpMethod.GET, "http://localhost/api/v1/groups/my",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        Response response = Response.builder()
                .status(404).reason("Not Found").request(request).headers(Map.of()).build();
        return (FeignException.NotFound) FeignException.errorStatus("GroupClient#getMyGroupId()", response);
    }

    // ================================================================
    // hasAdminRole
    // ================================================================

    @Test
    @DisplayName("roles 에 ADMIN 이 있으면 true")
    void hasAdminRole_trueWhenRolesContainAdmin() {
        assertThat(authService.hasAdminRole(TestJwt.withRoles("USER", "ADMIN"))).isTrue();
    }

    @Test
    @DisplayName("roles 에 ADMIN 이 없으면 false")
    void hasAdminRole_falseWhenRolesLackAdmin() {
        assertThat(authService.hasAdminRole(TestJwt.withRoles("USER"))).isFalse();
    }

    @Test
    @DisplayName("roles 클레임이 없으면 false")
    void hasAdminRole_falseWhenNoRolesClaim() {
        assertThat(authService.hasAdminRole(TestJwt.of("{\"sub\":1}"))).isFalse();
    }

    @Test
    @DisplayName("roles 가 배열이 아니면 false")
    void hasAdminRole_falseWhenRolesNotArray() {
        assertThat(authService.hasAdminRole(TestJwt.of("{\"roles\":\"ADMIN\"}"))).isFalse();
    }

    @Test
    @DisplayName("null 이거나 공백이면 false")
    void hasAdminRole_falseWhenNullOrBlank() {
        assertThat(authService.hasAdminRole(null)).isFalse();
        assertThat(authService.hasAdminRole("   ")).isFalse();
    }

    @Test
    @DisplayName("형식이 깨진 토큰이면 false")
    void hasAdminRole_falseWhenMalformedToken() {
        assertThat(authService.hasAdminRole("not-a-jwt")).isFalse();
        assertThat(authService.hasAdminRole("aaa.bbb.ccc")).isFalse();
    }

    // ================================================================
    // login
    // ================================================================

    @Test
    @DisplayName("login 성공 시 토큰과 사용자정보와 groupId 를 채운다")
    void login_populatesTokensUserInfoAndGroupId() {
        String accessToken = TestJwt.withUser(42, "홍길동");
        when(authClient.login(any())).thenReturn(
                loginResponse(new UserLoginResponse("SUCCESS", accessToken, null), "RT-123"));
        when(groupClient.getMyGroupId()).thenReturn(new MyGroupIdResponse(7L));

        LoginResult result = authService.login("user@test.com", "pw");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.userId()).isEqualTo(42L);
        assertThat(result.userName()).isEqualTo("홍길동");
        assertThat(result.groupId()).isEqualTo(7L);
        assertThat(result.accessToken()).isEqualTo(accessToken);
        assertThat(result.refreshToken()).isEqualTo("RT-123");
    }

    @Test
    @DisplayName("login 시 그룹이 없으면(404) groupId 는 null")
    void login_groupIdNullWhenNoGroup() {
        String accessToken = TestJwt.withUser(42, "홍길동");
        when(authClient.login(any())).thenReturn(
                loginResponse(new UserLoginResponse("SUCCESS", accessToken, null), "RT-123"));
        when(groupClient.getMyGroupId()).thenThrow(feignNotFound());

        LoginResult result = authService.login("user@test.com", "pw");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.groupId()).isNull();
    }

    @Test
    @DisplayName("login 응답이 PENDING_RESTORE 면 restoreToken 만 채우고 groupId 는 조회하지 않는다")
    void login_pendingRestorePopulatesRestoreTokenOnly() {
        when(authClient.login(any())).thenReturn(
                loginResponse(new UserLoginResponse("PENDING_RESTORE", null, "RESTORE-9"), null));

        LoginResult result = authService.login("user@test.com", "pw");

        assertThat(result.status()).isEqualTo("PENDING_RESTORE");
        assertThat(result.isPendingRestore()).isTrue();
        assertThat(result.restoreToken()).isEqualTo("RESTORE-9");
        assertThat(result.accessToken()).isNull();
        verify(groupClient, never()).getMyGroupId();
    }

    @Test
    @DisplayName("login 응답 본문이 비어 있으면 IllegalStateException")
    void login_throwsWhenBodyEmpty() {
        when(authClient.login(any())).thenReturn(loginResponse(null, null));

        assertThatThrownBy(() -> authService.login("u", "p"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ================================================================
    // hydrateFromAccessToken (소셜 로그인 완료 처리)
    // ================================================================

    @Test
    @DisplayName("hydrateFromAccessToken 은 토큰에서 사용자정보와 groupId 를 뽑는다")
    void hydrateFromAccessToken_extractsUserInfoAndGroupId() {
        String accessToken = TestJwt.withUser(11, "소셜유저");
        when(groupClient.getMyGroupId()).thenReturn(new MyGroupIdResponse(3L));

        LoginResult result = authService.hydrateFromAccessToken(accessToken, "RT-social");

        assertThat(result.userId()).isEqualTo(11L);
        assertThat(result.userName()).isEqualTo("소셜유저");
        assertThat(result.groupId()).isEqualTo(3L);
        assertThat(result.accessToken()).isEqualTo(accessToken);
        assertThat(result.refreshToken()).isEqualTo("RT-social");
    }

    // ================================================================
    // reactivateConfirm (재활성화 후 로그인)
    // ================================================================

    @Test
    @DisplayName("reactivateConfirm 은 일반 로그인과 동일하게 LoginResult 를 만든다")
    void reactivateConfirm_buildsLoginResultLikeLogin() {
        String accessToken = TestJwt.withUser(5, "복구유저");
        when(authClient.reactivateConfirm(any())).thenReturn(
                loginResponse(new UserLoginResponse("SUCCESS", accessToken, null), "RT-r"));
        when(groupClient.getMyGroupId()).thenReturn(new MyGroupIdResponse(1L));

        LoginResult result = authService.reactivateConfirm("user@test.com", "123456");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.userId()).isEqualTo(5L);
        assertThat(result.groupId()).isEqualTo(1L);
    }

    // ================================================================
    // loginAdmin
    // ================================================================

    @Test
    @DisplayName("loginAdmin 은 groupId 를 조회하지 않고 null 로 둔다")
    void loginAdmin_doesNotResolveGroupId() {
        String accessToken = TestJwt.withUser(99, "관리자");
        when(adminClient.login(any())).thenReturn(
                loginResponse(new UserLoginResponse("SUCCESS", accessToken, null), "RT-admin"));

        LoginResult result = authService.loginAdmin("admin@test.com", "pw");

        assertThat(result.userId()).isEqualTo(99L);
        assertThat(result.userName()).isEqualTo("관리자");
        assertThat(result.groupId()).isNull();
        verify(groupClient, never()).getMyGroupId();
    }

    // ================================================================
    // logout
    // ================================================================

    @Test
    @DisplayName("logout 은 auth 호출이 실패해도 예외를 던지지 않는다")
    void logout_swallowsException() {
        when(authClient.logout()).thenThrow(new RuntimeException("auth down"));

        assertThatCode(() -> authService.logout()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("logout 정상 시 auth logout 을 호출한다")
    void logout_callsAuthClientLogout() {
        when(authClient.logout()).thenReturn(ResponseEntity.noContent().build());

        authService.logout();

        verify(authClient).logout();
    }
}
