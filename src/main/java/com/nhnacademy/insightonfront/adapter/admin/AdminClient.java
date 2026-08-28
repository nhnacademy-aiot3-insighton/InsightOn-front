package com.nhnacademy.insightonfront.adapter.admin;

import com.nhnacademy.insightonfront.adapter.admin.dto.AdminFindUsersResponse;
import com.nhnacademy.insightonfront.adapter.admin.dto.AdminUserDetailResponse;
import com.nhnacademy.insightonfront.domain.admin.dto.RoleChangeRequest;
import com.nhnacademy.insightonfront.domain.auth.dto.UserLoginRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 프론트 서버 → 게이트웨이(→ 인증 서버) 관리자 회원관리 호출.
 * auth의 AdminController(/api/v1/admin/**)에 대응한다.
 * name = 게이트웨이 서비스 ID, 게이트웨이가 /api/v1/admin/** 를 auth 로 라우팅한다.
 */
@FeignClient(
        name = "insighton-gateway",
        contextId = "adminClient",
        url = "${service-url.gateway}")
public interface AdminClient {

    //로그인
    @PostMapping(
            value = "/api/v1/admin/login",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<String> login(@RequestBody UserLoginRequest userLoginRequest);

    // 회원 목록 조회 (검색·페이징)
    @GetMapping("/api/v1/admin/users")
    ResponseEntity<Page<AdminFindUsersResponse>> findUsers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String status,   // Status enum name (예: ACTIVE)
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size);

    // 회원 상세 조회
    @GetMapping("/api/v1/admin/users/{userId}")
    ResponseEntity<AdminUserDetailResponse> findUserDetail(@PathVariable("userId") Long userId);

    // 회원 계정 차단
    @PostMapping("/api/v1/admin/users/{userId}/block")
    ResponseEntity<Void> block(@PathVariable("userId") Long userId);

    // 회원 계정 휴면 전환
    @PostMapping("/api/v1/admin/users/{userId}/sleep")
    ResponseEntity<Void> sleep(@PathVariable("userId") Long userId);

    // 회원 계정 활성화 (복구)
    @PostMapping("/api/v1/admin/users/{userId}/activate")
    ResponseEntity<Void> activate(@PathVariable("userId") Long userId);

    // 회원 권한 변경
    @PutMapping("/api/v1/admin/users/{userId}/roles")
    ResponseEntity<Void> changeRole(
            @PathVariable("userId") Long userId,
            @RequestBody RoleChangeRequest request);

    // 강제 로그아웃
    @PostMapping("/api/v1/admin/users/{userId}/force-logout")
    ResponseEntity<Void> forceLogout(@PathVariable("userId") Long userId);
}