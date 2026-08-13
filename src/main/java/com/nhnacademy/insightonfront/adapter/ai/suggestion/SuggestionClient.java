package com.nhnacademy.insightonfront.adapter.ai.suggestion;

import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.adapter.ai.suggestion.dto.SuggestionLogResponse;
import java.time.OffsetDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "insighton-gateway", contextId = "suggestionClient")
public interface SuggestionClient {

    @GetMapping("/api/v1/suggestions")
    PageResponse<SuggestionLogResponse> getSuggestionLogs(@RequestParam("groupId") Long groupId,
                                                          @RequestParam(value = "locationId", required = false) Long locationId,
                                                          @RequestParam(value = "from", required = false) OffsetDateTime from,
                                                          @RequestParam(value = "to", required = false) OffsetDateTime to,
                                                          @RequestParam("page") int page,
                                                          @RequestParam("size") int size,
                                                          @RequestHeader("X-User-Id") Long userId);

    @GetMapping("/api/v1/suggestions/{suggestionLogId}")
    SuggestionLogResponse getSuggestionLog(@PathVariable("suggestionLogId") Long suggestionLogId,
                                           @RequestHeader("X-User-Id") Long userId);
}
