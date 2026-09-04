package com.nhnacademy.insightonfront.adapter.admin.dto;

/**
 * 지정 가능한 회원 권한 하나. auth 의 {@code Role} enum + 메타데이터.
 * exclusive: 선택 시 다른 권한과 함께 가질 수 없음 / base: 배타 역할이 없을 때 항상 보유.
 */
public record RoleResponse(
        String name,
        String label,
        boolean exclusive,
        boolean base
) {
}
