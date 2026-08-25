package com.nhnacademy.insightonfront.domain.suggestion.service;

import com.nhnacademy.insightonfront.adapter.ai.suggestion.SuggestionClient;
import com.nhnacademy.insightonfront.adapter.ai.suggestion.dto.SuggestionLogResponse;
import com.nhnacademy.insightonfront.adapter.core.groupmember.GroupMemberClient;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberListResponse;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupRole;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.common.resolver.LocationNameResolver;
import com.nhnacademy.insightonfront.domain.suggestion.dto.SuggestionLogViewModel;
import feign.FeignException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * SuggestionClient(AI) 응답에 LocationNameResolver로 위치 이름을 붙여 화면용 뷰 모델로 조립한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestionLogViewService {

    private static final String UNKNOWN_LOCATION = "알 수 없는 위치";

    private static final Map<String, String> ACTUATOR_TYPE_LABELS = Map.of(
            "AIRCON", "에어컨",
            "AIR_PURIFIER", "공기청정기",
            "VENTILATION_FAN", "환풍기"
    );
    private static final Map<String, String> COMMAND_VALUE_LABELS = Map.ofEntries(
            Map.entry("ON", "켜기"), Map.entry("OFF", "끄기"),
            Map.entry("COOL", "냉방"), Map.entry("DRY", "제습"), Map.entry("FAN", "송풍"), Map.entry("AUTO", "자동"),
            Map.entry("SLEEP", "취침"), Map.entry("TURBO", "터보"),
            Map.entry("LOW", "약"), Map.entry("MID", "중"), Map.entry("HIGH", "강")
    );

    private final SuggestionClient suggestionClient;
    private final LocationNameResolver locationNameResolver;
    private final GroupMemberClient groupMemberClient;
    private final ObjectMapper objectMapper;

    public PageResponse<SuggestionLogViewModel> getSuggestionLogs(Long groupId, Long locationId,
                                                                    OffsetDateTime from, OffsetDateTime to,
                                                                    int page, int size) {
        PageResponse<SuggestionLogResponse> logs =
                suggestionClient.getSuggestionLogs(groupId, locationId, from, to, page, size);
        Map<Long, String> locationNames = locationNameResolver.resolve(groupId);
        return logs.map(s -> toViewModel(s, locationNames));
    }

    public SuggestionLogViewModel getSuggestionLog(Long suggestionLogId) {
        SuggestionLogResponse log = suggestionClient.getSuggestionLog(suggestionLogId);
        Map<Long, String> locationNames = locationNameResolver.resolve(log.groupId());
        return toViewModel(log, locationNames);
    }

    /**
     * 제안 수락. MANAGER 이상만 가능 — AI 서비스 API는 그룹 멤버십만 검증하고 역할은 안 보므로 여기서 막는다.
     */
    public SuggestionLogViewModel accept(Long suggestionLogId, Long groupId, Long userId) {
        requireManagerOrAbove(groupId, userId);
        SuggestionLogResponse log = suggestionClient.accept(suggestionLogId);
        Map<Long, String> locationNames = locationNameResolver.resolve(log.groupId());
        return toViewModel(log, locationNames);
    }

    /**
     * 제안 거절. MANAGER 이상만 가능 — 이유는 {@link #accept} 참고.
     */
    public SuggestionLogViewModel reject(Long suggestionLogId, Long groupId, Long userId) {
        requireManagerOrAbove(groupId, userId);
        SuggestionLogResponse log = suggestionClient.reject(suggestionLogId);
        Map<Long, String> locationNames = locationNameResolver.resolve(log.groupId());
        return toViewModel(log, locationNames);
    }

    /**
     * 화면에서 수락/거절 버튼을 보여줄지 판단할 때 쓴다. 권한이 없으면 예외 대신 false만 반환한다.
     */
    public boolean isManagerOrAbove(Long groupId, Long userId) {
        try {
            return groupMemberClient.getGroupMemberList(groupId).stream()
                    .filter(member -> member.userId().equals(userId))
                    .map(GroupMemberListResponse::groupRole)
                    .anyMatch(role -> role.ordinal() >= GroupRole.MANAGER.ordinal());
        } catch (FeignException.Forbidden e) {
            // 멤버 목록 조회 자체가 admin/manager 전용이라, 일반 멤버는 "내가 매니저인지" 확인하는
            // 이 호출에서부터 403을 받는다 - 매니저가 아니라는 뜻이므로 false로 처리한다.
            return false;
        }
    }

    private void requireManagerOrAbove(Long groupId, Long userId) {
        if (!isManagerOrAbove(groupId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MANAGER 이상만 제안을 수락/거절할 수 있습니다.");
        }
    }

    private SuggestionLogViewModel toViewModel(SuggestionLogResponse log, Map<Long, String> locationNames) {
        return new SuggestionLogViewModel(
                log.suggestionLogId(),
                locationNames.getOrDefault(log.locationId(), UNKNOWN_LOCATION),
                log.title(),
                log.suggestionText(),
                log.isAccepted(),
                buildActionSummary(log.actionPayload()),
                log.createdAt()
        );
    }

    /** actionPayload JSON({"actuatorType","command","commandValue",...})을 "환풍기 전원 켜기" 같은 문장으로 바꾼다. */
    private String buildActionSummary(String actionPayloadJson) {
        if (actionPayloadJson == null || actionPayloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(actionPayloadJson);
            String actuatorType = node.path("actuatorType").asText(null);
            String command = node.path("command").asText(null);
            String commandValue = node.path("commandValue").asText(null);
            if (actuatorType == null || command == null || commandValue == null) {
                return null;
            }

            String actuatorLabel = ACTUATOR_TYPE_LABELS.getOrDefault(actuatorType, actuatorType);
            if ("SET_TEMPERATURE".equals(command)) {
                return actuatorLabel + " 설정 온도 " + commandValue + "°C로 변경";
            }
            String valueLabel = COMMAND_VALUE_LABELS.getOrDefault(commandValue, commandValue);
            return actuatorLabel + " " + valueLabel;
        } catch (Exception e) {
            log.warn("actionPayload 파싱 실패: {}", actionPayloadJson, e);
            return null;
        }
    }
}
