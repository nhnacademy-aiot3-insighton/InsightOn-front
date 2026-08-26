package com.nhnacademy.insightonfront.adapter.core.groupmember.dto;

public record GroupMemberListResponse(
        Long groupMemberId,
        Long userId,
        GroupRole groupRole
) {
}
