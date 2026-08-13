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
@RequestMapping("/groups/{group-id}/location")
public class LocationController {
    private final LocationClient locationClient;

    @PostMapping("/create")
    public String createLocation(@RequestHeader Long userId,
                                 @PathVariable("group-id") Long groupId,
                                 @RequestBody LocationCreateRequest request) {
        locationClient.createLocation(groupId, request, userId);

        log.info("location이 생성 되었습니다. group ID : {}", groupId);

        return "redirect:/groups/" + groupId + "/location/list";
    }

    @GetMapping("/list")
    public String getLocationList(@RequestHeader Long userId,
                                  @PathVariable("group-id") Long groupId, Model model) {
        List<LocationListResponse> locationList = locationClient.getLocationList(groupId, userId);

        model.addAttribute("locationList", locationList);

        return "";
    }

    @GetMapping("/{location-id}")
    public String getLocation(@RequestHeader Long userId,
                              @PathVariable("group-id") Long groupId,
                              @PathVariable("location-id") Long locationId, Model model) {
        LocationDetailResponse location = locationClient.getLocation(groupId, locationId, userId);

        model.addAttribute("location", location);

        return "";
    }

    @PutMapping("/{location-id}/toggle-model")
    public String toggleAutoControlMode(@RequestHeader Long userId,
                                        @PathVariable("group-id") Long groupId,
                                        @PathVariable("location-id") Long locationId) {
        locationClient.toggleAutoControlMode(groupId, locationId, userId);

        log.info("모드가 변경 되었습니다. Group ID: {}, Location ID: {}", groupId, locationId);

        return "redirect:/groups/" + groupId + "/location/" + locationId;
    }

    @PutMapping("/{location-id}/update")
    public String updateName(@RequestHeader Long userId,
                             @PathVariable("group-id") Long groupId,
                             @PathVariable("location-id") Long locationId,
                             @RequestBody LocationUpdateRequest request) {

        locationClient.updateName(groupId, locationId, request, userId);

        log.info("location name이 변경 되었습니다. Location ID : {}, 변경된 이름 : {}", locationId, request.newLocationName());

        return "redirect:/groups/" + groupId + "/location/" + locationId;
    }

    @DeleteMapping("/{location-id}/delete")
    public String deleteLocation(@RequestHeader Long userId,
                                 @PathVariable("group-id") Long groupId,
                                 @PathVariable("location-id") Long locationId) {
        locationClient.deleteLocation(groupId, locationId, userId);

        log.info("location이 삭제되었습니다. Group ID : {}, 삭제된 Location ID : {}", groupId, locationId);

        return "redirect:/groups/" + groupId + "/location/list";
    }

}
