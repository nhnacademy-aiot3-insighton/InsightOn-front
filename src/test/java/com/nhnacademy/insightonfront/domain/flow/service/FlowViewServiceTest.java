package com.nhnacademy.insightonfront.domain.flow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nhnacademy.insightonfront.adapter.core.sensor.SensorClient;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.FlowClient;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowDefinitionResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowLinkResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowNodeResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowStatus;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.NodeType;
import com.nhnacademy.insightonfront.common.resolver.LocationNameResolver;
import com.nhnacademy.insightonfront.domain.flow.dto.FlowDetailViewModel;
import com.nhnacademy.insightonfront.domain.flow.dto.FlowStepViewModel;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class FlowViewServiceTest {

    @Test
    void eventGate를_조건과_동작_사이의_별도_단계로_표시한다() {
        long flowId = 10L;
        long groupId = 20L;
        long locationId = 30L;
        List<FlowNodeResponse> nodes = List.of(
                new FlowNodeResponse(1L, NodeType.LOCATION, Map.of()),
                new FlowNodeResponse(2L, NodeType.THRESHOLD,
                        Map.of("expression", "#metrics['temperature'] > 30")),
                new FlowNodeResponse(3L, NodeType.EVENT_GATE,
                        Map.of("requiredCount", 3, "countWindowSeconds", 300, "cooldownSeconds", 1800)),
                new FlowNodeResponse(4L, NodeType.ACTUATOR_CONTROL,
                        Map.of("actuatorType", "AIRCON", "command", "power", "commandValue", "ON"))
        );
        List<FlowLinkResponse> links = List.of(
                link(1L, flowId, 1L, 2L, "out"),
                link(2L, flowId, 2L, 3L, "true"),
                link(3L, flowId, 3L, 4L, "true")
        );
        FlowDefinitionResponse definition = new FlowDefinitionResponse(
                flowId, groupId, locationId, "고온 제어", null, FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-09-04T10:00:00+09:00"), nodes, links);
        FlowClient flowClient = proxy(FlowClient.class,
                (method, arguments) -> method.getName().equals("getFlow") ? definition : null);
        SensorClient sensorClient = proxy(SensorClient.class,
                (method, arguments) -> method.getName().equals("search") ? List.of() : null);
        LocationNameResolver locationNameResolver = new LocationNameResolver(null) {
            @Override
            public Map<Long, String> resolve(Long requestedGroupId) {
                return Map.of(locationId, "강의실");
            }
        };
        FlowViewService flowViewService = new FlowViewService(flowClient, locationNameResolver, sensorClient);

        FlowDetailViewModel detail = flowViewService.getFlow(flowId, groupId);

        assertThat(detail.hasCondition()).isTrue();
        assertThat(detail.hasEventGate()).isTrue();
        assertThat(detail.eventGateStepNumber()).isEqualTo(3);
        assertThat(detail.laneCount()).isEqualTo(4);

        FlowStepViewModel gate = detail.steps().stream()
                .filter(step -> step.nodeType() == NodeType.EVENT_GATE)
                .findFirst()
                .orElseThrow();
        assertThat(gate.roleLabel()).isEqualTo("실행 안전장치");
        assertThat(gate.fields())
                .extracting(field -> field.label() + ": " + field.value())
                .containsExactly("반복 확인: 5분 안에 3번 확인", "최소 실행 간격: 30분");

        assertThat(flowViewService.getFlowForEdit(flowId, groupId).status()).isEqualTo(FlowStatus.ACTIVE);
    }

    private FlowLinkResponse link(long linkId, long flowId, long sourceNodeId, long targetNodeId,
                                  String sourcePort) {
        return new FlowLinkResponse(linkId, flowId, sourceNodeId, targetNodeId, sourcePort, "in");
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, BiFunction<java.lang.reflect.Method, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (ignored, method, arguments) -> handler.apply(method, arguments));
    }
}
