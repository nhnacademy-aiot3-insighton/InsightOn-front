package com.nhnacademy.insightonfront.adapter.core.groupmember.dto;

public record GroupMemberListResponse(
        Long groupMemberId,
        Long userId,
        String userName,
        GroupRole groupRole
) {
}
