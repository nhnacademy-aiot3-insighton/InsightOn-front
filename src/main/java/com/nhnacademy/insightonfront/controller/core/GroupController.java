package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.ai.enginealert.EngineAlertClient;
import com.nhnacademy.insightonfront.adapter.ai.enginealert.dto.Severity;
import com.nhnacademy.insightonfront.adapter.core.gateway.GatewayClient;
import com.nhnacademy.insightonfront.adapter.core.gateway.dto.GatewayResponse;
import com.nhnacademy.insightonfront.adapter.core.group.GroupClient;
import com.nhnacademy.insightonfront.adapter.core.group.dto.GroupRequest;
import com.nhnacademy.insightonfront.adapter.core.group.dto.GroupResponse;
import com.nhnacademy.insightonfront.adapter.core.location.LocationClient;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationListResponse;
import com.nhnacademy.insightonfront.adapter.core.region.RegionClient;
import com.nhnacademy.insightonfront.adapter.core.sensor.SensorClient;
import com.nhnacademy.insightonfront.adapter.core.weather.WeatherClient;
import com.nhnacademy.insightonfront.adapter.core.weather.dto.WeatherDataDto;
import com.nhnacademy.insightonfront.common.service.GroupPermissionService;
import feign.FeignException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/my-group")
public class GroupController {

    private static final DateTimeFormatter BASE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter BASE_TIME = DateTimeFormatter.ofPattern("HHmm");

    private final GroupClient groupClient;
    private final LocationClient locationClient;
    private final SensorClient sensorClient;
    private final EngineAlertClient engineAlertClient;
    private final WeatherClient weatherClient;
    private final GatewayClient gatewayClient;
    private final RegionClient regionClient;
    private final GroupPermissionService groupPermissionService;

    @PostMapping("/create")
    public String createGroup(@RequestBody GroupRequest request) {

        groupClient.createGroup(request);

        return "redirect:/";
    }

    /**
     * 그룹 메인 페이지 — 그룹 정보 + 위치별 요약 + 위험 알람 건수 + 날씨/미세먼지를 한 화면에 모은다.
     */
    @GetMapping
    public String getMyGroup(@CookieValue(value = "groupId", required = false) Long groupId,
                             Model model,
                             HttpServletResponse response) {

        GroupResponse myGroup;
        if (groupId == null) {
            Long myGroupId = groupClient.getMyGroupId().groupId();
            myGroup = groupClient.getMyGroup(myGroupId);

            ResponseCookie cookie = ResponseCookie.from("groupId", groupId.toString())
                    .httpOnly(true)
                    .path("/")
                    .sameSite("Lax")
                    .maxAge(Duration.ofDays(15))
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        } else {
            myGroup = groupClient.getMyGroup(groupId);
        }

        List<LocationSummary> locationSummaries = safeLocationSummaries(groupId);

        model.addAttribute("myGroup", myGroup);
        model.addAttribute("locationCount", locationSummaries != null ? locationSummaries.size() : null);
        model.addAttribute("locationSummaries", locationSummaries);
        model.addAttribute("sensorCount", safeSensorTotal(groupId));
        model.addAttribute("criticalAlertCount", safeCriticalAlertCount(groupId));
        model.addAttribute("gateway", safeGateway(groupId));
        model.addAttribute("weather", safeWeather(groupId));

        return "group/detail";
    }

    /**
     * 그룹 관리 > 그룹 정보 탭 — 실제 그룹 데이터로 이름/소재지/설명/초대 코드를 보여준다.
     * 소재지의 시/군/구 목록을 여기서 미리 채워서 내려줘야, 화면 진입 시 JS가 비동기로 다시 불러올
     * 때까지 "먼저 시/도를 선택하세요"가 잠깐 보이는 깜빡임 없이 바로 현재 값이 선택된 채로 뜬다.
     */
    @GetMapping("/manage")
    public String manageInfo(@CookieValue(value = "groupId", required = false) Long groupId,
                             @CookieValue(value = "userId", required = false) Long userId,
                             Model model) {
        if (groupId == null) {
            return "redirect:/group-registration";
        }
        try {
            GroupResponse myGroup = groupClient.getMyGroup(groupId);
            model.addAttribute("myGroup", myGroup);
            model.addAttribute("states", regionClient.getStates());
            model.addAttribute("cities", safeCitiesForCurrentRegion(myGroup.groupRegion()));
            model.addAttribute("section", "info");
            // SUPER_MANAGER(그룹 소유자)면 "그룹 탈퇴" 대신 "그룹 삭제" 를 노출
            model.addAttribute("isSuperManager", groupPermissionService.isSuperManager(groupId, userId));
        } catch (Exception e) {
            log.warn("그룹 관리 정보 조회 실패 - groupId:{}", groupId, e);
            return "redirect:/group-registration";
        }
        return "group/manage";
    }

    private List<String> safeCitiesForCurrentRegion(String groupRegion) {
        if (groupRegion == null || !groupRegion.contains(" ")) {
            return Collections.emptyList();
        }
        try {
            return regionClient.getCities(groupRegion.split(" ", 2)[0]);
        } catch (Exception e) {
            log.warn("소재지 시/군/구 목록 조회 실패 - groupRegion:{}", groupRegion, e);
            return Collections.emptyList();
        }
    }

    /**
     * 초대 코드로 그룹 가입 화면
     */
    @GetMapping("/join")
    public String joinForm(@CookieValue(value = "accessToken", required = false) String accessToken,
                           @CookieValue(value = "userId", required = false) Long userId,
                           @CookieValue(value = "groupId", required = false) Long groupId) {
        if (accessToken == null || userId == null) {
            return "redirect:/login";
        }
        if (groupId != null) {
            return "redirect:/my-group";
        }
        return "group/join";
    }

    /** 초대 토큰으로 기존 그룹에 참가한다 — 그룹 생성 신청과 별개로, 이미 있는 그룹에 들어가는 경로. */
    @PostMapping("/join")
    public String joinGroup(@CookieValue(value = "userId", required = false) Long userId,
                            @RequestParam("inviteToken") String inviteToken,
                            RedirectAttributes redirectAttributes) {
        if (userId == null) {
            return "redirect:/login";
        }

        try {
            groupClient.joinGroup(inviteToken);
            return "redirect:/my-group";
        } catch (FeignException.Conflict e) {
            log.warn("그룹 참가 실패(409) - 이미 처리 대기 중이거나 소속된 그룹이 있음. inviteToken:{}", inviteToken);
            redirectAttributes.addFlashAttribute("joinError", "이미 처리 중인 가입 신청이 있거나 이미 소속된 그룹이 있어요.");
            return "redirect:/my-group/join";
        } catch (FeignException.NotFound e) {
            log.warn("그룹 참가 실패(404) - 유효하지 않거나 만료된 초대 토큰. inviteToken:{}", inviteToken);
            redirectAttributes.addFlashAttribute("joinError", "존재하지 않거나 만료된 초대 코드입니다.");
            return "redirect:/my-group/join";
        } catch (FeignException.BadRequest e) {
            log.warn("그룹 참가 실패(400) - 잘못된 요청. inviteToken:{}", inviteToken);
            redirectAttributes.addFlashAttribute("joinError", "초대 코드가 올바르지 않거나 참가 조건을 충족하지 못했습니다.");
            return "redirect:/my-group/join";
        } catch (Exception e) {
            log.warn("그룹 참가 실패 - inviteToken:{}", inviteToken, e);
            redirectAttributes.addFlashAttribute("joinError", "초대 코드가 올바르지 않거나 만료됐거나, 이미 처리 중인 가입 신청이 있어요.");
            return "redirect:/my-group/join";
        }
    }

    /**
     * 초대 코드로 그룹 정보 미리보기 (REST/AJAX API)
     */
    @GetMapping("/preview")
    @ResponseBody
    public ResponseEntity<?> getGroupPreview(@CookieValue(value = "groupId", required = false) Long groupId,
                                              @RequestParam("inviteToken") String inviteToken) {
        try {
            Long targetGroupId = groupId != null ? groupId : 0L;
            GroupResponse groupPreview = groupClient.getGroupPreview(targetGroupId, inviteToken);
            return ResponseEntity.ok(groupPreview);
        } catch (FeignException.NotFound e) {
            return ResponseEntity.status(404).body(Map.of("message", "유효하지 않거나 만료된 초대 코드입니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "초대 코드가 올바르지 않거나 만료되었습니다."));
        }
    }


    @PostMapping("/invite-token/new")
    public String newInviteToken(@CookieValue(value = "groupId", required = false) Long groupId,
                                 RedirectAttributes redirectAttributes) {
        if (groupId == null) {
            return "redirect:/group-registration";
        }
        try {
            groupClient.newInviteToken(groupId);
            log.info("토큰이 새로 발급되었습니다. Group ID : {}", groupId);
        } catch (FeignException.Forbidden e) {
            log.warn("토큰 재발급 권한 없음(403) - groupId:{}", groupId);
            redirectAttributes.addFlashAttribute("tokenError", "토큰 재발급 권한이 없습니다 (Manager 이상 필요).");
        } catch (FeignException.NotFound e) {
            log.warn("토큰 재발급 대상 그룹 없음(404) - groupId:{}", groupId);
            redirectAttributes.addFlashAttribute("tokenError", "존재하지 않는 그룹입니다.");
        } catch (Exception e) {
            log.warn("토큰 재발급 실패 - groupId:{}", groupId, e);
            redirectAttributes.addFlashAttribute("tokenError", "토큰 재발급 권한이 없거나 처리 중 실패했어요.");
        }

        return "redirect:/my-group/manage";
    }

    @PutMapping("/update")
    @ResponseBody
    public ResponseEntity<Void> updateGroup(@CookieValue("groupId") Long groupId,
                                            @RequestBody GroupRequest request) {
        try {
            groupClient.updateGroup(groupId, request);
            return ResponseEntity.noContent().build();
        } catch (FeignException.Conflict e) {
            log.warn("[GroupController] 그룹 정보 수정 충돌(409). 이미 사용 중인 그룹명입니다. groupId: {}", groupId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).build();
        } catch (FeignException.Forbidden e) {
            log.warn("[GroupController] 그룹 정보 수정 권한 없음(403). groupId: {}", groupId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        } catch (FeignException.NotFound e) {
            log.warn("[GroupController] 수정할 그룹을 찾을 수 없음(404). groupId: {}", groupId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).build();
        } catch (FeignException.BadRequest e) {
            log.warn("[GroupController] 그룹 정보 수정 잘못된 요청(400). groupId: {}", groupId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST).build();
        }
    }

    @DeleteMapping("/delete")
    public String deleteGroup(@CookieValue("groupId") Long groupId, RedirectAttributes redirectAttributes) {
        try {
            groupClient.deleteGroup(groupId);
            log.info("성공적으로 삭제되었습니다. Group ID : {}", groupId);
            return "redirect:/";
        } catch (FeignException.Forbidden e) {
            log.warn("그룹 삭제 권한 없음(403) - groupId:{}", groupId);
            redirectAttributes.addFlashAttribute("groupError", "그룹을 삭제할 권한이 없습니다.");
            return "redirect:/my-group/manage";
        } catch (FeignException.NotFound e) {
            log.warn("삭제할 그룹 존재하지 않음(404) - groupId:{}", groupId);
            return "redirect:/";
        }
    }

    private List<LocationSummary> safeLocationSummaries(Long groupId) {
        try {
            List<LocationListResponse> locations = locationClient.getLocationList(groupId);
            return locations.stream()
                    .map(loc -> new LocationSummary(
                            loc.locationId(),
                            loc.locationName(),
                            safeSensorCount(groupId, loc.locationId()),
                            safeLocationAlertCount(groupId, loc.locationId())))
                    .toList();
        } catch (Exception e) {
            log.warn("위치 목록 조회 실패 - groupId:{}", groupId, e);
            return Collections.emptyList();
        }
    }

    private int safeSensorCount(Long groupId, Long locationId) {
        try {
            return sensorClient.search(groupId, null, null, locationId, null).size();
        } catch (Exception e) {
            log.warn("센서 개수 조회 실패 - groupId:{}, locationId:{}", groupId, locationId, e);
            return 0;
        }
    }

    private long safeLocationAlertCount(Long groupId, Long locationId) {
        try {
            return engineAlertClient.getEngineAlerts(groupId, locationId, null, null, null, 0, 1)
                    .totalElements();
        } catch (Exception e) {
            log.warn("위치별 알람 건수 조회 실패 - groupId:{}, locationId:{}", groupId, locationId, e);
            return 0;
        }
    }

    private Integer safeSensorTotal(Long groupId) {
        try {
            return sensorClient.search(groupId, null, null, null, null).size();
        } catch (Exception e) {
            log.warn("전체 센서 개수 조회 실패 - groupId:{}", groupId, e);
            return null;
        }
    }

    private GatewayResponse safeGateway(Long groupId) {
        try {
            return gatewayClient.getByGroupId(groupId);
        } catch (FeignException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.warn("게이트웨이 조회 실패 - groupId:{}", groupId, e);
            return null;
        }
    }

    private Long safeCriticalAlertCount(Long groupId) {
        try {
            return engineAlertClient.getEngineAlerts(groupId, null, Severity.CRITICAL, null, null, 0, 1)
                    .totalElements();
        } catch (Exception e) {
            log.warn("엔진 알람 건수 조회 실패 - groupId:{}", groupId, e);
            return null;
        }
    }

    private WeatherDataDto safeWeather(Long groupId) {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            return weatherClient.getWeatherByGroupId(groupId, BASE_DATE.format(now), BASE_TIME.format(now));
        } catch (Exception e) {
            log.warn("날씨 조회 실패 - groupId:{}", groupId, e);
            return null;
        }
    }

    /** 위치 요약 행 하나 — 온도는 깔끔한 조회 API가 없어서 뺐고, 대신 실제로 셀 수 있는 값만 담는다. */
    public record LocationSummary(Long locationId, String locationName, int sensorCount, long alertCount) {}
}
