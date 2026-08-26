package com.nhnacademy.insightonfront.domain.flow.service;

import com.nhnacademy.insightonfront.adapter.core.groupmember.GroupMemberClient;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberListResponse;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupRole;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Flow 쓰기 권한을 기존 그룹 역할 계약(MANAGER 이상)에 맞춰 판단한다. */
@Service
@RequiredArgsConstructor
public class FlowPermissionService {

    private final GroupMemberClient groupMemberClient;

    public boolean isManagerOrAbove(Long groupId, Long userId) {
        if (groupId == null || userId == null) {
            return false;
        }
        try {
            return groupMemberClient.getGroupMemberList(groupId).stream()
                    .filter(member -> userId.equals(member.userId()))
                    .map(GroupMemberListResponse::groupRole)
                    .anyMatch(role -> role.ordinal() >= GroupRole.MANAGER.ordinal());
        } catch (FeignException exception) {
            if (exception.status() == HttpStatus.UNAUTHORIZED.value()
                    || exception.status() == HttpStatus.FORBIDDEN.value()) {
                throw new ResponseStatusException(HttpStatus.valueOf(exception.status()),
                        "그룹 권한을 확인할 수 없습니다.", exception);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "그룹 멤버십 정보를 조회하지 못했습니다.", exception);
        }
    }

    public void requireManagerOrAbove(Long groupId, Long userId) {
        if (!isManagerOrAbove(groupId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "MANAGER 이상만 Flow를 생성하거나 변경할 수 있습니다.");
        }
    }
}
