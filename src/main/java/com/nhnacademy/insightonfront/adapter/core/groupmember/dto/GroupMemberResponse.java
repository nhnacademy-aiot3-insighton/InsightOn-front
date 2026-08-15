package com.nhnacademy.insightonfront.adapter.core.groupmember.dto;

import java.time.OffsetDateTime;

public record GroupMemberResponse(
        Long userId,
        Long groupId,
        GroupRole groupRole,
        String userName,
        String userPhoneNumber,
        OffsetDateTime joinedAt
) {
}
