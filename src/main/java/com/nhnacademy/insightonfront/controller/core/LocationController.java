package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.location.LocationClient;
import com.nhnacademy.insightonfront.adapter.core.location.dto.AutoControlMode;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationCreateRequest;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationDetailResponse;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationListResponse;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationUpdateRequest;
import com.nhnacademy.insightonfront.common.resolver.LocationNameResolver;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * groupId는 쿠키에서 읽는다(한 유저 = 한 그룹). locationId는 한 그룹 안에서도 여러 위치를
 * 오갈 수 있어 세션이 아니라 경로 변수로 받는다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/my-group/location")
public class LocationController {
    private final LocationClient locationClient;
    private final LocationNameResolver locationNameResolver;

    @PostMapping("/create")
    public String createLocation(@CookieValue(value = "groupId", required = false) Long groupId,
                                 @RequestParam String locationName,
                                 @RequestParam AutoControlMode autoControlMode,
                                 RedirectAttributes redirectAttributes) {
        if (groupId == null) {
            throw new IllegalArgumentException("소속된 그룹이 없습니다.");
        }
        try {
            locationClient.createLocation(groupId, new LocationCreateRequest(locationName, autoControlMode));
            locationNameResolver.invalidate(groupId);
            log.info("location이 생성 되었습니다. group ID : {}", groupId);
        } catch (FeignException e) {
            log.warn("location 생성 실패 - groupId:{}", groupId, e);
            redirectAttributes.addFlashAttribute("createError", "위치 추가에 실패했어요. 잠시 후 다시 시도해주세요.");
        }

        return "redirect:/my-group/location/list";
    }

    @GetMapping("/list")
    public String getLocationList(@CookieValue(value = "groupId", required = false) Long groupId, Model model) {
        if (groupId == null) {
            return "redirect:/group-registration";
        }
        List<LocationListResponse> locationList = locationClient.getLocationList(groupId);

        model.addAttribute("locationList", locationList);

        return "location/list";
    }

    @GetMapping("/{location-id}")
    public String getLocation(@CookieValue(value = "groupId", required = false) Long groupId,
                              @PathVariable("location-id") Long locationId, Model model) {
        if (groupId == null) {
            return "redirect:/group-registration";
        }
        LocationDetailResponse location = locationClient.getLocation(groupId, locationId);

        model.addAttribute("location", location);

        return "";
    }

    @PostMapping("/{location-id}/toggle-mode")
    public String toggleAutoControlMode(@CookieValue(value = "groupId", required = false) Long groupId,
                                        @PathVariable("location-id") Long locationId,
                                        @RequestParam(required = false) String redirect) {
        if (groupId == null) {
            throw new IllegalArgumentException("소속된 그룹이 없습니다.");
        }
        locationClient.toggleAutoControlMode(groupId, locationId);
        locationNameResolver.invalidate(groupId);

        log.info("모드가 변경 되었습니다. Group ID: {}, Location ID: {}", groupId, locationId);

        return "redirect:" + (redirect != null ? redirect : "/my-group/location/list");
    }

    @PutMapping("/{location-id}/update")
    @ResponseBody
    public ResponseEntity<Void> updateName(@CookieValue(value = "groupId", required = false) Long groupId,
                             @PathVariable("location-id") Long locationId,
                             @RequestBody LocationUpdateRequest request) {
        if (groupId == null) {
            throw new IllegalArgumentException("소속된 그룹이 없습니다.");
        }

        locationClient.updateName(groupId, locationId, request);
        locationNameResolver.invalidate(groupId);

        log.info("location name이 변경 되었습니다. Location ID : {}, 변경된 이름 : {}", locationId, request.newLocationName());

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{location-id}/delete")
    @ResponseBody
    public ResponseEntity<Void> deleteLocation(@CookieValue(value = "groupId", required = false) Long groupId,
                                 @PathVariable("location-id") Long locationId) {
        if (groupId == null) {
            throw new IllegalArgumentException("소속된 그룹이 없습니다.");
        }
        locationClient.deleteLocation(groupId, locationId);
        locationNameResolver.invalidate(groupId);

        log.info("location이 삭제되었습니다. Group ID : {}, 삭제된 Location ID : {}", groupId, locationId);

        return ResponseEntity.noContent().build();
    }

}
