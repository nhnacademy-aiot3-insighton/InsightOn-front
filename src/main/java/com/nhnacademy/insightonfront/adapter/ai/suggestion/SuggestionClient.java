package com.nhnacademy.insightonfront.adapter.ai.suggestion;

import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.adapter.ai.suggestion.dto.SuggestionLogResponse;
import java.time.OffsetDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * userId는 안 넘긴다 — 게이트웨이가 Authorization을 검증해서 X-User-Id로 바꿔 AI 서비스에 넘겨준다.
 */
@FeignClient(name = "insighton-gateway", contextId = "suggestionClient", url = "${service-url.gateway}")
public interface SuggestionClient {

    @GetMapping("/api/v1/suggestions")
    PageResponse<SuggestionLogResponse> getSuggestionLogs(@RequestParam("groupId") Long groupId,
                                                          @RequestParam(value = "locationId", required = false) Long locationId,
                                                          // 시간수정 — 어노테이션 없으면 Feign이 서버 기본 로케일(한국어)로 수정
                                                          @RequestParam(value = "from", required = false)
                                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                                          @RequestParam(value = "to", required = false)
                                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
                                                          @RequestParam("page") int page,
                                                          @RequestParam("size") int size);

    @GetMapping("/api/v1/suggestions/{suggestionLogId}")
    SuggestionLogResponse getSuggestionLog(@PathVariable("suggestionLogId") Long suggestionLogId);

    @PostMapping("/api/v1/suggestions/{suggestionLogId}/accept")
    SuggestionLogResponse accept(@PathVariable("suggestionLogId") Long suggestionLogId);

    @PostMapping("/api/v1/suggestions/{suggestionLogId}/reject")
    SuggestionLogResponse reject(@PathVariable("suggestionLogId") Long suggestionLogId);
}
