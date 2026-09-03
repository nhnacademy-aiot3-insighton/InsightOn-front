package com.nhnacademy.insightonfront.domain.admin;

import com.nhnacademy.insightonfront.adapter.admin.AdminClient;
import com.nhnacademy.insightonfront.adapter.admin.dto.AdminFindUsersResponse;
import com.nhnacademy.insightonfront.adapter.admin.dto.AdminUserDetailResponse;
import com.nhnacademy.insightonfront.adapter.admin.dto.RoleResponse;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.domain.admin.dto.RolesUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
    public PageResponse<AdminFindUsersResponse> findUsers(String email, String userName,
                                                         String status, int page, int size) {
        return adminClient.findUsers(email, userName, status, page, size).getBody();
    }

    /** 회원 상세 조회 */
    public AdminUserDetailResponse findUserDetail(Long userId) {
        return adminClient.findUserDetail(userId).getBody();
    }

    /** 지정 가능한 권한 목록 */
    public List<RoleResponse> findRoles() {
        return adminClient.findRoles().getBody();
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

    /** 회원 권한 전체 교체 (roles 가 최종 상태) */
    public void updateRoles(Long userId, List<String> roles) {
        adminClient.updateRoles(userId, new RolesUpdateRequest(roles));
    }

    /** 강제 로그아웃 */
    public void forceLogout(Long userId) {
        adminClient.forceLogout(userId);
    }
}