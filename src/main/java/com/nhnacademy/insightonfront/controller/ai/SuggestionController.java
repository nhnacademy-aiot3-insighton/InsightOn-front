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
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * AI가 만든 제안 로그를 조회하고, MANAGER 이상만 수락/거절할 수 있다. userId·groupId는 세션에서 읽는다(한 유저 = 한 그룹).
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/suggestions")
public class SuggestionController {

    private final SuggestionLogViewService suggestionLogViewService;
    private final LocationClient locationClient;

    @GetMapping
    public String list(@SessionAttribute(value = "userId", required = false) Long userId,
                        @SessionAttribute(value = "groupId", required = false) Long groupId,
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
                suggestionLogViewService.getSuggestionLogs(groupId, locationId, from, to, page, size, userId);
        List<LocationListResponse> locations = locationClient.getLocationList(groupId, userId);

        model.addAttribute("suggestions", suggestions);
        model.addAttribute("locations", locations);
        model.addAttribute("selectedLocationId", locationId);
        return "suggestion/list";
    }

    @GetMapping("/{suggestion-log-id}")
    public String detail(@SessionAttribute(value = "userId", required = false) Long userId,
                          @SessionAttribute(value = "groupId", required = false) Long groupId,
                          @PathVariable("suggestion-log-id") Long suggestionLogId,
                          Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        SuggestionLogViewModel suggestion = suggestionLogViewService.getSuggestionLog(suggestionLogId, userId);
        model.addAttribute("suggestion", suggestion);
        model.addAttribute("canManage", suggestionLogViewService.isManagerOrAbove(groupId, userId));
        return "suggestion/detail";
    }

    @PostMapping("/{suggestion-log-id}/accept")
    public String accept(@SessionAttribute(value = "userId", required = false) Long userId,
                          @SessionAttribute(value = "groupId", required = false) Long groupId,
                          @PathVariable("suggestion-log-id") Long suggestionLogId) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        suggestionLogViewService.accept(suggestionLogId, groupId, userId);
        return "redirect:/suggestions/" + suggestionLogId;
    }

    @PostMapping("/{suggestion-log-id}/reject")
    public String reject(@SessionAttribute(value = "userId", required = false) Long userId,
                          @SessionAttribute(value = "groupId", required = false) Long groupId,
                          @PathVariable("suggestion-log-id") Long suggestionLogId) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        suggestionLogViewService.reject(suggestionLogId, groupId, userId);
        return "redirect:/suggestions/" + suggestionLogId;
    }
}
