package com.nhnacademy.insightonfront.domain.flow.dto;

import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowNodeRequest;
import java.util.List;

/**
 * 수정 화면용 뷰 모델. 상세 응답의 nodeId 기반 Link를 clientNodeKey 기반으로 변환해서 담는다
 * (Rule Engine 상세 응답은 nodeId를 쓰지만 생성·수정 요청은 clientNodeKey를 쓰기 때문).
 */
public record FlowEditViewModel(
        Long flowId,
        Long locationId,
        String name,
        String description,
        List<FlowNodeRequest> nodes,
        List<FlowLinkRequest> links
) {
}
