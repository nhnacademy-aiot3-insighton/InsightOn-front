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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * 위치 안의 액추에이터(에어컨/공기청정기/환풍기)를 조작한다. 타입별로 켤 수 있는 명령·값이
 * {@link ActuatorCommandPreset}에 고정되어 있어, 화면은 그 규칙대로 토글/선택/범위 위젯만 그린다 —
 * 사용자가 임의의 명령 문자열을 입력하는 자유 입력은 없다.
 * <p>groupId는 쿠키에서 읽는다(한 유저 = 한 그룹). locationId는 한 그룹 안에서도 여러 위치를
 * 오갈 수 있어 계속 경로 변수로 둔다.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/locations/{location-id}/actuators")
public class ActuatorController {

    private final ActuatorClient actuatorClient;
    private final LocationClient locationClient;

    @GetMapping
    public String panel(@CookieValue(value = "userId", required = false) Long userId,
                         @CookieValue(value = "groupId", required = false) Long groupId,
                         @PathVariable("location-id") Long locationId,
                         Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        // actuatorId(생성 순서 = auto-increment PK) 기준으로 프론트에서 고정 정렬한다
        List<ActuatorResponse> actuators = actuatorClient.getActuatorsByLocationId(groupId, locationId).stream()
                .sorted(Comparator.comparing(ActuatorResponse::actuatorId))
                .toList();
        LocationDetailResponse location = locationClient.getLocation(groupId, locationId);

        Map<String, Object> commandRules = new LinkedHashMap<>();
        for (ActuatorType type : ActuatorType.values()) {
            commandRules.put(type.name(), ActuatorCommandPreset.forTemplate(type));
        }

        model.addAttribute("actuators", actuators);
        model.addAttribute("location", location);
        model.addAttribute("locationId", locationId);
        model.addAttribute("commandRules", commandRules);
        model.addAttribute("actuatorTypes", ActuatorType.values());
        return "actuator/panel";
    }

    @PostMapping
    @ResponseBody
    public Long create(@CookieValue(value = "groupId", required = false) Long groupId,
                        @PathVariable("location-id") Long locationId,
                        @RequestBody ActuatorCreateForm form) {
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
    public void updateState(@CookieValue(value = "groupId", required = false) Long groupId,
                             @PathVariable("actuator-id") Long actuatorId,
                             @RequestBody Map<String, Object> newState) {
        actuatorClient.updateActuatorState(groupId, actuatorId, newState);
    }

    // 액추에이터 카드 "실행 이력" 모델이 페이지 단위로 호출
    @GetMapping("/{actuator-id}/logs")
    @ResponseBody
    public PageResponse<ActuatorRunLogResponse> getLogs(@CookieValue(value = "groupId", required = false) Long groupId,
                                                          @PathVariable("actuator-id") Long actuatorId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return actuatorClient.getActuatorRunLogs(groupId, actuatorId, page, size);
    }

    @PutMapping("/{actuator-id}/name")
    @ResponseBody
    public void updateName(@CookieValue(value = "groupId", required = false) Long groupId,
                            @PathVariable("actuator-id") Long actuatorId,
                            @RequestBody ActuatorNameUpdateRequest request) {
        actuatorClient.updateActuatorName(groupId, actuatorId, request);
    }

    @DeleteMapping("/{actuator-id}")
    @ResponseBody
    public void delete(@CookieValue(value = "groupId", required = false) Long groupId,
                        @PathVariable("actuator-id") Long actuatorId) {
        actuatorClient.deleteActuatorById(groupId, actuatorId);
    }

    /** 액추에이터 추가 폼 — locationId는 URL에서 이미 정해지므로 이름·타입만 받는다. */
    public record ActuatorCreateForm(String name, ActuatorType actuatorType) {}
}
