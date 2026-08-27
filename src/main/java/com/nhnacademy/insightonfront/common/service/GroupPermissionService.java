package com.nhnacademy.insightonfront.common.service;

import com.nhnacademy.insightonfront.adapter.core.groupmember.GroupMemberClient;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberListResponse;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupRole;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 액추에이터/센서 등 여러 도메인이 공통으로 쓰는 "MANAGER 이상" 쓰기 권한 판단
 * (FlowPermissionService/SuggestionLogViewService와 동일한 계약).
 * <p>멤버 목록 조회 자체가 관리자 전용이라, 일반 멤버는 "내가 매니저인지" 확인하는 이 호출에서부터
 * 403을 받음 — 매니저가 아니라는 뜻이므로 예외를 던지지 않고 false로 처리함.
 */
@Service
@RequiredArgsConstructor
public class GroupPermissionService {

    private final GroupMemberClient groupMemberClient;

    public boolean isManagerOrAbove(Long groupId, Long userId) {
        try {
            return groupMemberClient.getGroupMemberList(groupId).stream()
                    .filter(member -> member.userId().equals(userId))
                    .map(GroupMemberListResponse::groupRole)
                    .anyMatch(role -> role.ordinal() >= GroupRole.MANAGER.ordinal());
        } catch (FeignException.Forbidden e) {
            return false;
        }
    }

    public void requireManagerOrAbove(Long groupId, Long userId, String action) {
        if (!isManagerOrAbove(groupId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MANAGER 이상만 " + action + " 수 있습니다.");
        }
    }
}
