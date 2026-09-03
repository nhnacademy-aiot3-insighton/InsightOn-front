package com.nhnacademy.insightonfront.adapter.auth.mypage;

import com.nhnacademy.insightonfront.adapter.auth.mypage.dto.MyInfoUpdateRequest;
import com.nhnacademy.insightonfront.adapter.auth.mypage.dto.PasswordChangeRequest;
import com.nhnacademy.insightonfront.domain.mypage.dto.MyInfoResponse;
import com.nhnacademy.insightonfront.domain.mypage.dto.OauthResponse;
import com.nhnacademy.insightonfront.domain.mypage.dto.RoleResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 프론트 서버 → 게이트웨이(→ 인증 서버) 마이페이지 호출.
 * accessToken이 필요한 API라 인증 인터셉터(AuthorizationRequestInterceptor)를 그대로 탄다.
 * X-User-Id는 front가 넣지 않는다 — 게이트웨이가 accessToken을 검증해 자동 주입한다.
 */
@FeignClient(
        name = "insighton-gateway",
        contextId = "mypageClient",
        url = "${service-url.gateway}")
public interface MypageClient {

    @GetMapping("/api/v1/users/me")
    ResponseEntity<MyInfoResponse> findMyInfo();

    @PutMapping("/api/v1/users/me")
    ResponseEntity<Void> updateMyInfo(@RequestBody MyInfoUpdateRequest request);

    @DeleteMapping("/api/v1/users/me")
    ResponseEntity<Void> withdraw();

    @PutMapping("/api/v1/users/me/password")
    ResponseEntity<Void> changePassword(@RequestBody PasswordChangeRequest request);

    @GetMapping("/api/v1/users/me/roles")
    ResponseEntity<List<RoleResponse>> findMyRoles();

    @GetMapping("/api/v1/users/me/oauths")
    ResponseEntity<List<OauthResponse>> findMyOauths();

    // 소셜 계정 신규 연동은 브라우저 주도 왕복이 필요해 front 의 GET /oauth/link/{provider} 가 담당한다.

    @DeleteMapping("/api/v1/users/me/oauths/{oauthId}")
    ResponseEntity<Void> unlinkOauth(@PathVariable("oauthId") Long oauthId);
}
