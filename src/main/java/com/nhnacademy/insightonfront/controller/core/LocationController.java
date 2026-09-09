package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.location.LocationClient;
import com.nhnacademy.insightonfront.adapter.core.location.dto.*;
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
    private final com.nhnacademy.insightonfront.common.service.GroupPermissionService groupPermissionService;

    @PostMapping("/create")
    public String createLocation(@CookieValue(value = "groupId", required = false) Long groupId,
                                 @RequestParam String locationName,
                                 @RequestParam(required = false) AutoControlMode autoControlMode,
                                 RedirectAttributes redirectAttributes) {
        if (groupId == null) {
            throw new IllegalArgumentException("소속된 그룹이 없습니다.");
        }
        try {
            AutoControlMode mode = autoControlMode != null ? autoControlMode : AutoControlMode.SUGGESTION;
            locationClient.createLocation(groupId, new LocationCreateRequest(locationName, mode));
            locationNameResolver.invalidate(groupId);
            log.info("location이 생성 되었습니다. group ID : {}", groupId);
        } catch (FeignException.Conflict e) {
            log.warn("location 생성 실패(409) - 이미 존재하는 위치 이름. groupId:{}, locationName:{}", groupId, locationName);
            redirectAttributes.addFlashAttribute("createError", "이미 존재하는 위치 이름입니다.");
        } catch (FeignException.Forbidden e) {
            log.warn("location 생성 권한 없음(403) - groupId:{}", groupId);
            redirectAttributes.addFlashAttribute("createError", "위치를 추가할 권한이 없습니다 (Manager 이상 필요).");
        } catch (FeignException.BadRequest e) {
            log.warn("location 생성 잘못된 요청(400) - groupId:{}, locationName:{}", groupId, locationName);
            redirectAttributes.addFlashAttribute("createError", "위치 이름이 올바르지 않거나 빈 값입니다.");
        } catch (FeignException e) {
            log.warn("location 생성 실패 - groupId:{}", groupId, e);
            redirectAttributes.addFlashAttribute("createError", "위치 추가에 실패했어요. 잠시 후 다시 시도해주세요.");
        }

        return "redirect:/my-group/location/list";
    }

    @GetMapping("/list")
    public String getLocationList(@CookieValue(value = "userId", required = false) Long userId,
                                  @CookieValue(value = "groupId", required = false) Long groupId, Model model) {
        if (groupId == null) {
            return "redirect:/group-registration";
        }
        List<LocationListResponse> locationList = locationClient.getLocationList(groupId);
        boolean isManager = (userId != null) && groupPermissionService.isManagerOrAbove(groupId, userId);

        model.addAttribute("locationList", locationList);
        model.addAttribute("isManager", isManager);

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

        return "location/detail";
    }

    @PostMapping("/{location-id}/toggle-mode")
    public String toggleAutoControlMode(@CookieValue(value = "groupId", required = false) Long groupId,
                                        @PathVariable("location-id") Long locationId,
                                        @RequestParam(required = false) String redirect,
                                        RedirectAttributes redirectAttributes) {
        if (groupId == null) {
            throw new IllegalArgumentException("소속된 그룹이 없습니다.");
        }
        try {
            locationClient.toggleAutoControlMode(groupId, locationId);
            locationNameResolver.invalidate(groupId);
            log.info("모드가 변경 되었습니다. Group ID: {}, Location ID: {}", groupId, locationId);
        } catch (FeignException.Forbidden e) {
            log.warn("자동 제어 모드 변경 권한 없음(403) - groupId:{}, locationId:{}", groupId, locationId);
            redirectAttributes.addFlashAttribute("modeError", "자동 제어 모드를 변경할 권한이 없습니다.");
        } catch (FeignException.NotFound e) {
            log.warn("위치 존재하지 않음(404) - groupId:{}, locationId:{}", groupId, locationId);
            redirectAttributes.addFlashAttribute("modeError", "존재하지 않는 위치입니다.");
        } catch (Exception e) {
            log.warn("자동 제어 모드 변경 실패 - groupId:{}, locationId:{}", groupId, locationId, e);
            redirectAttributes.addFlashAttribute("modeError", "모드 변경에 실패했습니다.");
        }

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

        try {
            locationClient.updateName(groupId, locationId, request);
            locationNameResolver.invalidate(groupId);
            log.info("location name이 변경 되었습니다. Location ID : {}, 변경된 이름 : {}", locationId, request.newLocationName());
            return ResponseEntity.noContent().build();
        } catch (FeignException.Conflict e) {
            log.warn("[LocationController] 위치 이름 변경 충돌(409). 이미 사용 중인 이름입니다. locationId: {}", locationId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).build();
        } catch (FeignException.Forbidden e) {
            log.warn("[LocationController] 위치 이름 변경 권한 없음(403). locationId: {}", locationId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        } catch (FeignException.NotFound e) {
            log.warn("[LocationController] 변경할 위치를 찾을 수 없음(404). locationId: {}", locationId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).build();
        } catch (FeignException.BadRequest e) {
            log.warn("[LocationController] 위치 이름 변경 잘못된 요청(400). locationId: {}", locationId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST).build();
        }
    }

    @DeleteMapping("/{location-id}/delete")
    @ResponseBody
    public ResponseEntity<Void> deleteLocation(@CookieValue(value = "groupId", required = false) Long groupId,
                                 @PathVariable("location-id") Long locationId) {
        if (groupId == null) {
            throw new IllegalArgumentException("소속된 그룹이 없습니다.");
        }

        try {
            locationClient.deleteLocation(groupId, locationId);
            locationNameResolver.invalidate(groupId);
            log.info("location이 삭제되었습니다. Group ID : {}, 삭제된 Location ID : {}", groupId, locationId);
            return ResponseEntity.noContent().build();
        } catch (FeignException.Forbidden e) {
            log.warn("[LocationController] 위치 삭제 권한 없음(403). locationId: {}", locationId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        } catch (FeignException.NotFound e) {
            log.warn("[LocationController] 삭제할 위치를 찾을 수 없음(404). locationId: {}", locationId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).build();
        }
    }

}
