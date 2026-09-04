package com.nhnacademy.insightonfront.domain.admin.dto;

import java.util.List;

/**
 * 회원 권한 전체 교체 요청. 목록에 담긴 권한이 최종 상태가 된다
 * (auth 가 유지/추가/삭제를 자동 계산). auth 의 {@code RolesUpdateRequest} 와 대응.
 */
public record RolesUpdateRequest(
        List<String> roles
) {
}
