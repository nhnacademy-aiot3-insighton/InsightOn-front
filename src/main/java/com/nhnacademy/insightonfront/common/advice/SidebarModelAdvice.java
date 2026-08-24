package com.nhnacademy.insightonfront.common.advice;

import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationListResponse;
import com.nhnacademy.insightonfront.common.resolver.LocationNameResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 페이지의 사이드바에서 쓰는 위치 목록을 한 곳에서 채워준다 - 각 컨트롤러가 매번
 * locationList를 model에 담을 필요 없게.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class SidebarModelAdvice {

    private final LocationNameResolver locationNameResolver;

    @ModelAttribute("sidebarLocations")
    public List<LocationListResponse> sidebarLocations(
            @CookieValue(value = "groupId", required = false) Long groupId) {
        if (groupId == null) {
            return List.of();
        }
        return locationNameResolver.getLocations(groupId);
    }
}
