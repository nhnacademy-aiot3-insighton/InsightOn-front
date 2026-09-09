package com.nhnacademy.insightonfront.domain.flow.dto;

import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.NodeType;
import java.util.List;

/** Rule Engine Node를 기술 용어 없이 설명하기 위한 상세 화면 전용 모델. */
public record FlowStepViewModel(
        Long nodeId,
        NodeType nodeType,
        String roleLabel,
        String title,
        String description,
        String icon,
        List<FlowStepFieldViewModel> fields
) {
}
