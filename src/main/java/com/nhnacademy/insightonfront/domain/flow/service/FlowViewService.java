package com.nhnacademy.insightonfront.domain.flow.service;

import com.nhnacademy.insightonfront.adapter.core.sensor.SensorClient;
import com.nhnacademy.insightonfront.adapter.core.sensor.dto.SensorResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.FlowClient;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowDefinitionResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowLinkResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowNodeResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowStatus;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.NodeType;
import com.nhnacademy.insightonfront.common.resolver.LocationNameResolver;
import com.nhnacademy.insightonfront.domain.flow.dto.FlowDetailViewModel;
import com.nhnacademy.insightonfront.domain.flow.dto.FlowEditViewModel;
import com.nhnacademy.insightonfront.domain.flow.dto.FlowStepFieldViewModel;
import com.nhnacademy.insightonfront.domain.flow.dto.FlowStepViewModel;
import com.nhnacademy.insightonfront.domain.flow.dto.FlowViewModel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * FlowClient(Rule Engine) 응답에 LocationNameResolver로 위치 이름을 붙여 화면용 뷰 모델로 조립
 */
@Service
@RequiredArgsConstructor
public class FlowViewService {

    private static final String UNKNOWN_LOCATION = "알 수 없는 위치";
    private static final Pattern THRESHOLD_EXPRESSION = Pattern.compile(
            "^#metrics\\[['\"]([^'\"]+)['\"]]\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)$");
    private static final Map<String, String> METRIC_LABELS = Map.of(
            "temperature", "온도", "humidity", "습도", "co2", "CO₂", "illuminance", "조도");
    private static final Map<String, String> METRIC_UNITS = Map.of(
            "temperature", "°C", "humidity", "%", "co2", "ppm", "illuminance", "lx");
    private static final Map<String, String> OPERATOR_LABELS = Map.of(
            ">", "초과", ">=", "이상", "<", "미만", "<=", "이하", "==", "같음", "!=", "다름");

    private final FlowClient flowClient;
    private final LocationNameResolver locationNameResolver;
    private final SensorClient sensorClient;

    public List<FlowViewModel> getFlows(Long groupId, FlowStatus status) {
        return getFlows(groupId, status, null);
    }

    // locationId는 백엔드가 아니라 여기서 거른다 - Rule Engine의 locationId 조회는 status도
    // 함께 있어야 해서, "전체 상태" 필터(status=null)와 궁합이 안 맞기 때문이다.
    public List<FlowViewModel> getFlows(Long groupId, FlowStatus status, Long locationId) {
        List<FlowResponse> flows = flowClient.list(groupId, status);
        Map<Long, String> locationNames = locationNameResolver.resolve(groupId);
        return flows.stream()
                .map(flow -> toViewModel(flow, locationNames))
                .filter(flow -> locationId == null || locationId.equals(flow.locationId()))
                .toList();
    }

    private FlowViewModel toViewModel(FlowResponse flow, Map<Long, String> locationNames) {
        return new FlowViewModel(
                flow.flowId(),
                flow.name(),
                flow.description(),
                flow.status(),
                flow.locationId(),
                locationNames.getOrDefault(flow.locationId(), UNKNOWN_LOCATION),
                flow.createdAt()
        );
    }

    public FlowDetailViewModel getFlow(Long flowId, Long groupId) {
        FlowDefinitionResponse flow = flowClient.getFlow(flowId, groupId);
        Map<Long, String> locationNames = locationNameResolver.resolve(groupId);
        String locationName = locationNames.getOrDefault(flow.locationId(), UNKNOWN_LOCATION);
        List<FlowNodeResponse> orderedNodes = orderNodes(flow.nodes(), flow.links());
        return new FlowDetailViewModel(
                flow.flowId(),
                flow.name(),
                flow.description(),
                flow.status(),
                locationName,
                flow.createdAt(),
                toSteps(orderedNodes, groupId, locationName),
                orderedNodes,
                flow.links()
        );
    }

    private List<FlowStepViewModel> toSteps(List<FlowNodeResponse> nodes, Long groupId, String locationName) {
        Map<Long, String> sensorNames = loadSensorNames(groupId);
        return nodes.stream()
                .map(node -> toStep(node, sensorNames, locationName))
                .toList();
    }

    private Map<Long, String> loadSensorNames(Long groupId) {
        try {
            return sensorClient.search(groupId, null, null, null, null).stream()
                    .collect(Collectors.toMap(SensorResponse::sensorId, SensorResponse::sensorName));
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private FlowStepViewModel toStep(FlowNodeResponse node, Map<Long, String> sensorNames, String locationName) {
        Map<String, Object> configuration = node.configuration() == null ? Map.of() : node.configuration();
        return switch (node.nodeType()) {
            case SENSOR -> sensorStep(node, configuration, sensorNames);
            case LOCATION -> new FlowStepViewModel(node.nodeId(), node.nodeType(), "시작",
                    locationName + " 전체 측정값", "이 위치의 센서에서 새 측정값이 들어오면 시작합니다.",
                    "ti-map-pin", List.of());
            case THRESHOLD -> thresholdStep(node, configuration);
            case ALERT -> alertStep(node, configuration);
            case SCHEDULE -> new FlowStepViewModel(node.nodeId(), node.nodeType(), "시작", "예약한 시간에 시작",
                    "설정한 일정에 맞춰 Flow를 시작합니다.", "ti-calendar-time",
                    fields(new FlowStepFieldViewModel("일정", text(configuration, "cron", "설정 없음"))));
            case TIME_WINDOW -> new FlowStepViewModel(node.nodeId(), node.nodeType(), "조건", "운영 시간 확인",
                    "현재 시간이 설정한 범위에 포함되는지 확인합니다.", "ti-clock",
                    fields(new FlowStepFieldViewModel("시간", text(configuration, "startTime", "-")
                            + " ~ " + text(configuration, "endTime", "-"))));
            case TIMER -> new FlowStepViewModel(node.nodeId(), node.nodeType(), "조건", "반복 간격 확인",
                    "같은 동작이 너무 자주 실행되지 않도록 간격을 둡니다.", "ti-hourglass",
                    fields(new FlowStepFieldViewModel("간격", duration(number(configuration, "intervalSeconds", 0)))));
            case ACTUATOR_CONTROL -> new FlowStepViewModel(node.nodeId(), node.nodeType(), "동작", "기기 제어",
                    "연결된 기기에 제어 명령을 보냅니다.", "ti-toggle-right",
                    fields(new FlowStepFieldViewModel("명령", text(configuration, "command", "기기 제어"))));
            case EXTERNAL_NOTIFICATION -> new FlowStepViewModel(node.nodeId(), node.nodeType(), "동작", "외부 알림 보내기",
                    "이메일 또는 Telegram으로 알림을 보냅니다.", "ti-send",
                    fields(new FlowStepFieldViewModel("채널", text(configuration, "channel", "알림"))));
        };
    }

    private FlowStepViewModel sensorStep(FlowNodeResponse node, Map<String, Object> configuration,
                                         Map<Long, String> sensorNames) {
        long sensorId = number(configuration, "sensorId", 0);
        String sensorName = sensorNames.getOrDefault(sensorId,
                sensorId > 0 ? "센서 " + sensorId : "선택한 센서");
        return new FlowStepViewModel(node.nodeId(), node.nodeType(), "시작", sensorName,
                "이 센서에서 새 측정값이 들어오면 시작합니다.", "ti-device-desktop-analytics", List.of());
    }

    private FlowStepViewModel thresholdStep(FlowNodeResponse node, Map<String, Object> configuration) {
        String expression = text(configuration, "expression", "");
        Matcher matcher = THRESHOLD_EXPRESSION.matcher(expression);
        if (!matcher.matches()) {
            return new FlowStepViewModel(node.nodeId(), node.nodeType(), "조건", "측정값 조건 확인",
                    "등록된 측정값 조건을 만족하는지 확인합니다.", "ti-adjustments-horizontal", List.of());
        }
        String metricKey = matcher.group(1);
        String metric = METRIC_LABELS.getOrDefault(metricKey, metricKey);
        String unit = METRIC_UNITS.getOrDefault(metricKey, "");
        String condition = metric + "가 " + matcher.group(3) + unit + " "
                + OPERATOR_LABELS.getOrDefault(matcher.group(2), matcher.group(2));
        return new FlowStepViewModel(node.nodeId(), node.nodeType(), "조건", condition,
                "이 조건을 만족하면 다음 단계로 진행합니다.", "ti-adjustments-horizontal", List.of());
    }

    private FlowStepViewModel alertStep(FlowNodeResponse node, Map<String, Object> configuration) {
        List<FlowStepFieldViewModel> fields = new ArrayList<>();
        fields.add(new FlowStepFieldViewModel("중요도", severity(text(configuration, "severity", "INFO"))));
        int requiredCount = (int) number(configuration, "requiredCount", 3);
        fields.add(new FlowStepFieldViewModel("확인 횟수", requiredCount <= 1
                ? "조건을 한 번 만족하면"
                : "조건을 " + requiredCount + "회 만족하면"));
        int timeout = (int) number(configuration, "countTimeoutSeconds", 300);
        if (requiredCount >= 2 && timeout > 0) {
            fields.add(new FlowStepFieldViewModel("확인 시간", duration(timeout) + " 안에"));
        }
        int cooldown = (int) number(configuration, "cooldownSeconds", 1800);
        fields.add(new FlowStepFieldViewModel("재알림 대기", cooldown <= 0
                ? "제한 없음"
                : "알림 후 " + duration(cooldown) + " 동안 다시 보내지 않음"));
        return new FlowStepViewModel(node.nodeId(), node.nodeType(), "동작",
                text(configuration, "title", "알림 보내기"),
                text(configuration, "message", "조건을 만족하면 담당자에게 알립니다."),
                "ti-bell", List.copyOf(fields));
    }

    private List<FlowStepFieldViewModel> fields(FlowStepFieldViewModel... values) {
        return List.of(values);
    }

    private String text(Map<String, Object> configuration, String key, String fallback) {
        Object value = configuration.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private long number(Map<String, Object> configuration, String key, long fallback) {
        Object value = configuration.get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private String severity(String value) {
        return switch (value) {
            case "CRITICAL" -> "위험";
            case "WARNING" -> "경고";
            default -> "안내";
        };
    }

    private String duration(long seconds) {
        if (seconds <= 0) return "설정 없음";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        List<String> parts = new ArrayList<>();
        if (hours > 0) parts.add(hours + "시간");
        if (minutes > 0) parts.add(minutes + "분");
        if (remainingSeconds > 0 || parts.isEmpty()) parts.add(remainingSeconds + "초");
        return String.join(" ", parts);
    }

    /** 상세 화면에서 DB 저장 순서가 아니라 Trigger에서 출발하는 실행 순서로 Node를 보여준다. */
    private List<FlowNodeResponse> orderNodes(List<FlowNodeResponse> nodes, List<FlowLinkResponse> links) {
        Map<Long, FlowNodeResponse> nodesById = nodes.stream()
                .collect(Collectors.toMap(FlowNodeResponse::nodeId, node -> node));
        Map<Long, List<Long>> targetsBySourceId = new HashMap<>();
        for (FlowLinkResponse link : links) {
            targetsBySourceId.computeIfAbsent(link.sourceNodeId(), ignored -> new ArrayList<>())
                    .add(link.targetNodeId());
        }

        FlowNodeResponse trigger = nodes.stream()
                .filter(node -> isTrigger(node.nodeType()))
                .findFirst()
                .orElse(null);
        if (trigger == null) {
            return nodes;
        }

        List<FlowNodeResponse> ordered = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(trigger.nodeId());
        while (!pending.isEmpty()) {
            Long nodeId = pending.removeFirst();
            FlowNodeResponse node = nodesById.get(nodeId);
            if (node == null || !visited.add(nodeId)) {
                continue;
            }
            ordered.add(node);
            pending.addAll(targetsBySourceId.getOrDefault(nodeId, List.of()));
        }
        nodes.stream()
                .filter(node -> !visited.contains(node.nodeId()))
                .forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private boolean isTrigger(NodeType nodeType) {
        return nodeType == NodeType.SENSOR
                || nodeType == NodeType.LOCATION
                || nodeType == NodeType.SCHEDULE;
    }

    public FlowEditViewModel getFlowForEdit(Long flowId, Long groupId) {
        FlowDefinitionResponse flow = flowClient.getFlow(flowId, groupId);
        Map<Long, String> clientNodeKeys = flow.nodes().stream()
                .collect(Collectors.toMap(FlowNodeResponse::nodeId, node -> "node-" + node.nodeId()));

        List<FlowNodeRequest> nodes = flow.nodes().stream()
                .map(node -> new FlowNodeRequest(
                        clientNodeKeys.get(node.nodeId()),
                        node.nodeType(),
                        node.configuration()))
                .toList();
        List<FlowLinkRequest> links = flow.links().stream()
                .map(link -> new FlowLinkRequest(
                        clientNodeKeys.get(link.sourceNodeId()),
                        clientNodeKeys.get(link.targetNodeId()),
                        link.sourcePort(),
                        link.targetPort()))
                .toList();

        return new FlowEditViewModel(flow.flowId(), flow.locationId(), flow.name(), flow.description(), nodes, links);
    }
}
