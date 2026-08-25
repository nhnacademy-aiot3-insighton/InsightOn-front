package com.nhnacademy.insightonfront.controller.ai;

import com.nhnacademy.insightonfront.adapter.core.location.LocationClient;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationListResponse;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.domain.suggestion.dto.SuggestionLogViewModel;
import com.nhnacademy.insightonfront.domain.suggestion.service.SuggestionLogViewService;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.CookieValue;

/**
 * AI가 만든 제안 로그를 조회하고, MANAGER 이상만 수락/거절할 수 있다. groupId는 쿠키에서 읽는다
 * (한 유저 = 한 그룹). userId는 수락/거절 권한(그룹 내 역할) 확인에 실제로 필요해서 쿠키값을 쓴다
 * — 그 외 조회 호출들은 게이트웨이가 Authorization에서 뽑아주는 X-User-Id로 인증된다.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/my-group/suggestions")
public class SuggestionController {

    private final SuggestionLogViewService suggestionLogViewService;
    private final LocationClient locationClient;

    @GetMapping
    public String list(@CookieValue(value = "userId", required = false) Long userId,
                        @CookieValue(value = "groupId", required = false) Long groupId,
                        @RequestParam(required = false) Long locationId,
                        @RequestParam(required = false) OffsetDateTime from,
                        @RequestParam(required = false) OffsetDateTime to,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        PageResponse<SuggestionLogViewModel> suggestions =
                suggestionLogViewService.getSuggestionLogs(groupId, locationId, from, to, page, size);
        List<LocationListResponse> locations = locationClient.getLocationList(groupId);

        model.addAttribute("suggestions", suggestions);
        model.addAttribute("locations", locations);
        model.addAttribute("selectedLocationId", locationId);
        return "suggestion/list";
    }

    @GetMapping("/{suggestion-log-id}")
    public String detail(@CookieValue(value = "userId", required = false) Long userId,
                          @CookieValue(value = "groupId", required = false) Long groupId,
                          @PathVariable("suggestion-log-id") Long suggestionLogId,
                          Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        SuggestionLogViewModel suggestion = suggestionLogViewService.getSuggestionLog(suggestionLogId);
        model.addAttribute("suggestion", suggestion);
        model.addAttribute("canManage", suggestionLogViewService.isManagerOrAbove(groupId, userId));
        return "suggestion/detail";
    }

    @PostMapping("/{suggestion-log-id}/accept")
    public String accept(@CookieValue(value = "userId", required = false) Long userId,
                          @CookieValue(value = "groupId", required = false) Long groupId,
                          @PathVariable("suggestion-log-id") Long suggestionLogId) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        suggestionLogViewService.accept(suggestionLogId, groupId, userId);
        return "redirect:/suggestions/" + suggestionLogId;
    }

    @PostMapping("/{suggestion-log-id}/reject")
    public String reject(@CookieValue(value = "userId", required = false) Long userId,
                          @CookieValue(value = "groupId", required = false) Long groupId,
                          @PathVariable("suggestion-log-id") Long suggestionLogId) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        suggestionLogViewService.reject(suggestionLogId, groupId, userId);
        return "redirect:/suggestions/" + suggestionLogId;
    }
}
