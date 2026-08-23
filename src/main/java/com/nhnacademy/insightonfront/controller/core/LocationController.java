package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.location.LocationClient;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationCreateRequest;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationDetailResponse;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationListResponse;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/my-group/location")
public class LocationController {
    private final LocationClient locationClient;

    @PostMapping("/create")
    public String createLocation(@CookieValue(value = "userId", required = false) Long userId,
                                 @CookieValue(value = "groupId", required = false) Long groupId,
                                 @RequestBody LocationCreateRequest request) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        locationClient.createLocation(groupId, request, userId);

        log.info("location이 생성 되었습니다. group ID : {}", groupId);

        return "redirect:/my-group/location/list";
    }

    @GetMapping("/list")
    public String getLocationList(@CookieValue(value = "userId", required = false) Long userId,
                                  @CookieValue(value = "groupId", required = false) Long groupId, Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        List<LocationListResponse> locationList = locationClient.getLocationList(groupId, userId);

        model.addAttribute("locationList", locationList);

        return "";
    }

    // location 상세 정보를 조회할 수 있는 페이지도 필요
    @GetMapping("/{locationId}")
    public String getLocation(@CookieValue(value = "userId", required = false) Long userId,
                              @CookieValue(value = "groupId", required = false) Long groupId,
                              @PathVariable("locationId") Long locationId, Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        LocationDetailResponse location = locationClient.getLocation(groupId, locationId, userId);

        model.addAttribute("location", location);

        return "";
    }

    @PutMapping("/{locationId}/toggle-mode")
    public String toggleAutoControlMode(@CookieValue(value = "userId", required = false) Long userId,
                                        @CookieValue(value = "groupId", required = false) Long groupId,
                                        @PathVariable("locationId") Long locationId) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        locationClient.toggleAutoControlMode(groupId, locationId, userId);

        log.info("모드가 변경 되었습니다. Group ID: {}, Location ID: {}", groupId, locationId);

        return "redirect:/my-group/location/" + locationId;
    }

    @PutMapping("/{locationId}/update")
    public String updateName(@CookieValue(value = "userId", required = false) Long userId,
                             @CookieValue(value = "groupId", required = false) Long groupId,
                             @PathVariable("locationId") Long locationId,
                             @RequestBody LocationUpdateRequest request) {

        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        locationClient.updateName(groupId, locationId, request, userId);

        log.info("location name이 변경 되었습니다. Location ID : {}, 변경된 이름 : {}", locationId, request.newLocationName());

        return "redirect:/my-group/location/" + locationId;
    }

    @DeleteMapping("/{locationId}/delete")
    public String deleteLocation(@CookieValue(value = "userId", required = false) Long userId,
                                 @CookieValue(value = "groupId", required = false) Long groupId,
                                 @PathVariable("locationId") Long locationId) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        locationClient.deleteLocation(groupId, locationId, userId);

        log.info("location이 삭제되었습니다. Group ID : {}, 삭제된 Location ID : {}", groupId, locationId);

        return "redirect:/my-group/location/list";
    }

}
