package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.actuator.ActuatorClient;
import com.nhnacademy.insightonfront.adapter.core.actuator.ActuatorCommandPreset;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorNameUpdateRequest;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorRequest;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorResponse;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorRunLogResponse;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorType;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.CommandType;
import com.nhnacademy.insightonfront.adapter.core.location.LocationClient;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationDetailResponse;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import com.nhnacademy.insightonfront.common.service.GroupPermissionService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.CookieValue;

/**
 * 위치 안의 액추에이터(에어컨/공기청정기/환풍기)를 조작함. 타입별로 켤 수 있는 명령·값이
 * {@link ActuatorCommandPreset}에 고정되어 있어, 화면은 그 규칙대로 토글/선택/범위 위젯만 그림 —
 * 사용자가 임의의 명령 문자열을 입력하는 자유 입력은 없음.
 * <p>groupId는 쿠키에서 읽음(한 유저 = 한 그룹). locationId는 한 그룹 안에서도 여러 위치를
 * 오갈 수 있어 계속 경로 변수로 둠.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/locations/{location-id}/actuators")
public class ActuatorController {

    private final ActuatorClient actuatorClient;
    private final LocationClient locationClient;
    private final GroupPermissionService groupPermissionService;

    @GetMapping
    public String panel(@CookieValue(value = "userId", required = false) Long userId,
                         @CookieValue(value = "groupId", required = false) Long groupId,
                         @PathVariable("location-id") Long locationId,
                         Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        // actuatorId(생성 순서 = auto-increment PK) 기준으로 프론트에서 고정 정렬함
        List<ActuatorResponse> actuators = actuatorClient.getActuatorsByLocationId(groupId, locationId).stream()
                .sorted(Comparator.comparing(ActuatorResponse::actuatorId))
                .toList();
        LocationDetailResponse location = locationClient.getLocation(groupId, locationId);

        Map<String, Object> commandRules = new LinkedHashMap<>();
        for (ActuatorType type : ActuatorType.values()) {
            commandRules.put(type.name(), ActuatorCommandPreset.forTemplate(type));
        }

        // 타입별 일괄 조작 박스를 그릴지 판단 — 템플릿에서 SpEL 셀렉션(.?[])으로 하면
        // th:each 변수를 셀렉션 안에서 못 읽어서(EL1008E) 여기서 미리 계산해 넘김
        Set<ActuatorType> presentTypes = actuators.stream()
                .map(ActuatorResponse::actuatorType)
                .collect(Collectors.toSet());

        // 개별 액추에이터 카드 목록을 타입별로 묶어서 보여주기 위한 그룹핑 — 이유는 위와 동일(SpEL
        // 셀렉션은 th:each 변수를 못 읽음)
        Map<String, List<ActuatorResponse>> actuatorsByType = actuators.stream()
                .collect(Collectors.groupingBy(a -> a.actuatorType().name(), LinkedHashMap::new, Collectors.toList()));

        model.addAttribute("actuators", actuators);
        model.addAttribute("actuatorsByType", actuatorsByType);
        model.addAttribute("location", location);
        model.addAttribute("locationId", locationId);
        model.addAttribute("commandRules", commandRules);
        model.addAttribute("actuatorTypes", ActuatorType.values());
        model.addAttribute("presentTypes", presentTypes);
        // MEMBER는 조작/추가/수정/삭제/실행이력을 못 보게 화면에서 숨김 — 서버에서도 각 엔드포인트에서 다시 막음
        model.addAttribute("canManage", groupPermissionService.isManagerOrAbove(groupId, userId));
        return "actuator/panel";
    }

    @PostMapping
    @ResponseBody
    public Long create(@CookieValue(value = "userId", required = false) Long userId,
                        @CookieValue(value = "groupId", required = false) Long groupId,
                        @PathVariable("location-id") Long locationId,
                        @RequestBody ActuatorCreateForm form) {
        // 추가는 MANAGER 이상만 — MEMBER가 호출하면 core에 요청 보내기 전에 여기서 403
        groupPermissionService.requireManagerOrAbove(groupId, userId, "액추에이터를 추가할");
        return actuatorClient.createActuator(groupId,
                new ActuatorRequest(locationId, form.name(), form.actuatorType(), defaultState(form.actuatorType())));
    }

    // 타입별 기본 상태 — 생성 직후 카드에 바로 의미 있는 값이 보이도록 채워줌
    private static Map<String, Object> defaultState(ActuatorType actuatorType) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(CommandType.POWER_STATUS.getStateKey(), "OFF");
        switch (actuatorType) {
            case AIRCON -> {
                state.put(CommandType.OPERATION_MODE.getStateKey(), "COOL");
                state.put(CommandType.SET_TEMPERATURE.getStateKey(), 18);
            }
            case AIR_PURIFIER -> state.put(CommandType.OPERATION_MODE.getStateKey(), "AUTO");
            case VENTILATION_FAN -> state.put(CommandType.OPERATION_MODE.getStateKey(), "LOW");
        }
        return state;
    }

    @PutMapping("/{actuator-id}/state")
    @ResponseBody
    public void updateState(@CookieValue(value = "userId", required = false) Long userId,
                             @CookieValue(value = "groupId", required = false) Long groupId,
                             @PathVariable("actuator-id") Long actuatorId,
                             @RequestBody Map<String, Object> newState) {
        // 조작(전원/모드/온도)도 MANAGER 이상만 — MEMBER가 호출하면 여기서 403
        groupPermissionService.requireManagerOrAbove(groupId, userId, "액추에이터를 조작할");
        actuatorClient.updateActuatorState(groupId, actuatorId, newState);
    }

    // 액추에이터 카드 "실행 이력" 모델이 페이지 단위로 호출
    @GetMapping("/{actuator-id}/logs")
    @ResponseBody
    public PageResponse<ActuatorRunLogResponse> getLogs(@CookieValue(value = "userId", required = false) Long userId,
                                                          @CookieValue(value = "groupId", required = false) Long groupId,
                                                          @PathVariable("actuator-id") Long actuatorId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        // 실행 이력 조회도 MANAGER 이상만 — MEMBER가 호출하면 여기서 403
        groupPermissionService.requireManagerOrAbove(groupId, userId, "실행 이력을 조회할");
        return actuatorClient.getActuatorRunLogs(groupId, actuatorId, page, size);
    }

    @PutMapping("/{actuator-id}/name")
    @ResponseBody
    public void updateName(@CookieValue(value = "userId", required = false) Long userId,
                            @CookieValue(value = "groupId", required = false) Long groupId,
                            @PathVariable("actuator-id") Long actuatorId,
                            @RequestBody ActuatorNameUpdateRequest request) {
        // 이름 수정도 MANAGER 이상만 — MEMBER가 호출하면 여기서 403
        groupPermissionService.requireManagerOrAbove(groupId, userId, "액추에이터 이름을 수정할");
        actuatorClient.updateActuatorName(groupId, actuatorId, request);
    }

    @DeleteMapping("/{actuator-id}")
    @ResponseBody
    public void delete(@CookieValue(value = "userId", required = false) Long userId,
                        @CookieValue(value = "groupId", required = false) Long groupId,
                        @PathVariable("actuator-id") Long actuatorId) {
        // 삭제도 MANAGER 이상만 — MEMBER가 호출하면 여기서 403
        groupPermissionService.requireManagerOrAbove(groupId, userId, "액추에이터를 삭제할");
        actuatorClient.deleteActuatorById(groupId, actuatorId);
    }

    // 액추에이터 추가 폼 — locationId는 URL에서 이미 정해지므로 이름·타입만 받음
    public record ActuatorCreateForm(String name, ActuatorType actuatorType) {}
}
