package com.nhnacademy.insightonfront.domain.flow.dto;

import java.util.List;

/** 휴지통 비우기에서 성공·실패한 Flow를 브라우저에 각각 알려준다. */
public record FlowTrashEmptyResult(
        List<Long> deletedFlowIds,
        List<Long> failedFlowIds
) {
}
