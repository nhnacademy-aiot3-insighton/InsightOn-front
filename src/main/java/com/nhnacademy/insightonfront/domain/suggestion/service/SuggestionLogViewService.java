package com.nhnacademy.insightonfront.domain.suggestion.service;

import com.nhnacademy.insightonfront.adapter.ai.suggestion.SuggestionClient;
import com.nhnacademy.insightonfront.adapter.ai.suggestion.dto.SuggestionLogResponse;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.common.resolver.LocationNameResolver;
import com.nhnacademy.insightonfront.domain.suggestion.dto.SuggestionLogViewModel;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SuggestionClient(AI) 응답에 LocationNameResolver로 위치 이름을 붙여 화면용 뷰 모델로 조립한다.
 */
@Service
@RequiredArgsConstructor
public class SuggestionLogViewService {

    private static final String UNKNOWN_LOCATION = "알 수 없는 위치";

    private final SuggestionClient suggestionClient;
    private final LocationNameResolver locationNameResolver;

    public PageResponse<SuggestionLogViewModel> getSuggestionLogs(Long groupId, Long locationId,
                                                                    OffsetDateTime from, OffsetDateTime to,
                                                                    int page, int size, Long userId) {
        PageResponse<SuggestionLogResponse> logs =
                suggestionClient.getSuggestionLogs(groupId, locationId, from, to, page, size, userId);
        Map<Long, String> locationNames = locationNameResolver.resolve(groupId, userId);
        return logs.map(s -> toViewModel(s, locationNames));
    }

    public SuggestionLogViewModel getSuggestionLog(Long suggestionLogId, Long userId) {
        SuggestionLogResponse log = suggestionClient.getSuggestionLog(suggestionLogId, userId);
        Map<Long, String> locationNames = locationNameResolver.resolve(log.groupId(), userId);
        return toViewModel(log, locationNames);
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
