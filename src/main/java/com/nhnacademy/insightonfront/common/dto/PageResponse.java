package com.nhnacademy.insightonfront.common.dto;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI/Core 서비스의 목록 조회 API가 공통으로 반환하는 Spring Data Page JSON 구조를 그대로 미러링한다.
 */
public record PageResponse<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty,
        int numberOfElements
) {

    /**
     * content만 화면용 뷰 모델로 바꾸고 페이지네이션 메타데이터는 그대로 유지한다.
     */
    public <R> PageResponse<R> map(Function<T, R> mapper) {
        List<R> mapped = content.stream().map(mapper).collect(Collectors.toList());
        return new PageResponse<>(mapped, number, size, totalElements, totalPages, first, last, empty, numberOfElements);
    }
}
