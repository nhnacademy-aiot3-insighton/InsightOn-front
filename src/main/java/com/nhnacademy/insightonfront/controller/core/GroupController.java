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
import com.nhnacademy.insightonfront.adapter.core.sensor.SensorClient;
import com.nhnacademy.insightonfront.adapter.core.weather.WeatherClient;
import com.nhnacademy.insightonfront.adapter.core.weather.dto.WeatherDataDto;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

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

    @PostMapping("/create")
    public String createGroup(@RequestBody GroupRequest request) {

        groupClient.createGroup(request);

        return "redirect:/";
    }

    /**
     * 그룹 메인 페이지 — 그룹 정보 + 위치별 요약 + 위험 알람 건수 + 날씨/미세먼지를 한 화면에 모은다.
     * 날씨·알람·위치 같은 부가 데이터는 하나가 실패해도 나머지는 보여줘야 해서 개별적으로 실패를 삼킨다
     * (특히 날씨는 core 쪽에 GlobalExceptionHandler 미등록 버그가 있어 실패 시 메시지 없는 500이 옴).
     * <p>위치별 "현재 온도"는 깔끔한 API가 없어서 뺐다 — HourlyTelemetryStatClient가 시간별 평균을
     * JSON 문자열로 주긴 하지만 위치마다 무슨 센서가 있는지에 따라 온도 필드가 있을지 없을지도
     * 다르고 키 이름도 확실치 않아서, 대신 실제로 깔끔하게 셀 수 있는 센서 개수·알람 건수로 채웠다.
     */
    @GetMapping
    public String getMyGroup(@CookieValue(value = "groupId", required = false) Long groupId,
                             Model model) {

        GroupResponse myGroup = groupClient.getMyGroup(groupId);
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

    /** 그룹 관리 &gt; 그룹 정보 탭 — 실제 그룹 데이터로 이름/소재지/설명/초대 코드를 보여준다. */
    @GetMapping("/manage")
    public String manageInfo(@CookieValue(value = "groupId", required = false) Long groupId,
                             Model model) {

        model.addAttribute("myGroup", groupClient.getMyGroup(groupId));
        model.addAttribute("section", "info");
        return "group/manage";
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
        } catch (Exception e) {
            log.warn("그룹 참가 실패 - inviteToken:{}", inviteToken, e);
            redirectAttributes.addFlashAttribute("joinError", "초대 코드가 올바르지 않거나 만료됐거나, 이미 처리 중인 가입 신청이 있어요.");
            return "redirect:/";
        }
    }

    @GetMapping("/preview")
    public String getGroupPreview(@CookieValue("groupId") Long groupId,
                                  @RequestParam("inviteToken") String inviteToken,
                                  Model model) {

        GroupResponse groupPreview = groupClient.getGroupPreview(groupId, inviteToken);

        model.addAttribute("groupPreview", groupPreview);

        return "";
    }


    @PostMapping("/invite-token/new")
    public String newInviteToken(@CookieValue(value = "groupId", required = false) Long groupId) {

        groupClient.newInviteToken(groupId);

        log.info("토큰이 새로 발급되었습니다. Group ID : {}", groupId);

        return "redirect:/my-group/manage";
    }

    @PutMapping("/update")
    public String updateGroup(@CookieValue("groupId") Long groupId,
                              @RequestBody GroupRequest request) {

        groupClient.updateGroup(groupId, request);

        return "redirect:/groups/" + groupId + "/my-group";
    }

    @DeleteMapping("/delete")
    public String deleteGroup(@CookieValue("groupId") Long groupId) {

        groupClient.deleteGroup(groupId);

        log.info("성공적으로 삭제되었습니다. Group ID : {}", groupId);
        return "redirect:/";
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
