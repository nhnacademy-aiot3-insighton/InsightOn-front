package com.nhnacademy.insightonfront.domain.admin;

import com.nhnacademy.insightonfront.adapter.admin.AdminClient;
import com.nhnacademy.insightonfront.adapter.admin.dto.AdminFindUsersResponse;
import com.nhnacademy.insightonfront.adapter.admin.dto.AdminUserDetailResponse;
import com.nhnacademy.insightonfront.domain.admin.dto.RoleChangeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * 관리자 회원관리 서비스.
 * AdminClient(Feign)를 통해 auth의 AdminController로 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminClient adminClient;

    /** 회원 목록 조회 (검색·페이징) */
    public Page<AdminFindUsersResponse> findUsers(String email, String userName,
                                                  String status, int page, int size) {
        return adminClient.findUsers(email, userName, status, page, size).getBody();
    }

    /** 회원 상세 조회 */
    public AdminUserDetailResponse findUserDetail(Long userId) {
        return adminClient.findUserDetail(userId).getBody();
    }

    /** 회원 계정 차단 */
    public void block(Long userId) {
        adminClient.block(userId);
    }

    /** 회원 계정 휴면 전환 */
    public void sleep(Long userId) {
        adminClient.sleep(userId);
    }

    /** 회원 계정 활성화 (복구) */
    public void activate(Long userId) {
        adminClient.activate(userId);
    }

    /** 회원 권한 변경 */
    public void changeRole(Long userId, String role) {
        adminClient.changeRole(userId, new RoleChangeRequest(role));
    }

    /** 강제 로그아웃 */
    public void forceLogout(Long userId) {
        adminClient.forceLogout(userId);
    }
}