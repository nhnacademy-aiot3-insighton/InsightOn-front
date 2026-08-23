package com.nhnacademy.insightonfront.domain.suggestion.service;

import com.nhnacademy.insightonfront.adapter.ai.suggestion.SuggestionClient;
import com.nhnacademy.insightonfront.adapter.ai.suggestion.dto.SuggestionLogResponse;
import com.nhnacademy.insightonfront.adapter.core.groupmember.GroupMemberClient;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberListResponse;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupRole;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.common.resolver.LocationNameResolver;
import com.nhnacademy.insightonfront.domain.suggestion.dto.SuggestionLogViewModel;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * SuggestionClient(AI) 응답에 LocationNameResolver로 위치 이름을 붙여 화면용 뷰 모델로 조립한다.
 */
@Service
@RequiredArgsConstructor
public class SuggestionLogViewService {

    private static final String UNKNOWN_LOCATION = "알 수 없는 위치";

    private final SuggestionClient suggestionClient;
    private final LocationNameResolver locationNameResolver;
    private final GroupMemberClient groupMemberClient;

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
        return groupMemberClient.getGroupMemberList(groupId).stream()
                .filter(member -> member.userId().equals(userId))
                .map(GroupMemberListResponse::groupRole)
                .anyMatch(role -> role.ordinal() >= GroupRole.MANAGER.ordinal());
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
                log.actionPayload(),
                log.createdAt()
        );
    }
}
