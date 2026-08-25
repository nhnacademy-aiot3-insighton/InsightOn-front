package com.nhnacademy.insightonfront.domain.flow.dto;

/** Browser가 Flow API 오류를 같은 형태로 처리하도록 만드는 Front 오류 응답. */
public record FlowErrorResponse(
        int status,
        String message
) {
}
