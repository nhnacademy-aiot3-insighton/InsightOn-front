package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.actuator.ActuatorClient;
import com.nhnacademy.insightonfront.adapter.core.actuator.ActuatorCommandPreset;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorNameUpdateRequest;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorRequest;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorResponse;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorType;
import com.nhnacademy.insightonfront.adapter.core.location.LocationClient;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationDetailResponse;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * 위치 안의 액추에이터(에어컨/공기청정기/환풍기)를 조작한다. 타입별로 켤 수 있는 명령·값이
 * {@link ActuatorCommandPreset}에 고정되어 있어, 화면은 그 규칙대로 토글/선택/범위 위젯만 그린다 —
 * 사용자가 임의의 명령 문자열을 입력하는 자유 입력은 없다.
 * <p>groupId는 세션에서 읽는다(한 유저 = 한 그룹). locationId는 한 그룹 안에서도 여러 위치를
 * 오갈 수 있어 계속 경로 변수로 둔다.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/locations/{location-id}/actuators")
public class ActuatorController {

    private final ActuatorClient actuatorClient;
    private final LocationClient locationClient;

    @GetMapping
    public String panel(@SessionAttribute(value = "userId", required = false) Long userId,
                         @SessionAttribute(value = "groupId", required = false) Long groupId,
                         @PathVariable("location-id") Long locationId,
                         Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        List<ActuatorResponse> actuators = actuatorClient.getActuatorsByLocationId(userId, groupId, locationId);
        LocationDetailResponse location = locationClient.getLocation(groupId, locationId, userId);

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
    public Long create(@SessionAttribute(value = "userId", required = false) Long userId,
                        @SessionAttribute(value = "groupId", required = false) Long groupId,
                        @PathVariable("location-id") Long locationId,
                        @RequestBody ActuatorCreateForm form) {
        return actuatorClient.createActuator(userId, groupId,
                new ActuatorRequest(locationId, form.name(), form.actuatorType(), Map.of()));
    }

    @PutMapping("/{actuator-id}/state")
    @ResponseBody
    public void updateState(@SessionAttribute(value = "userId", required = false) Long userId,
                             @SessionAttribute(value = "groupId", required = false) Long groupId,
                             @PathVariable("actuator-id") Long actuatorId,
                             @RequestBody Map<String, Object> newState) {
        actuatorClient.updateActuatorState(userId, groupId, actuatorId, newState);
    }

    @PutMapping("/{actuator-id}/name")
    @ResponseBody
    public void updateName(@SessionAttribute(value = "userId", required = false) Long userId,
                            @SessionAttribute(value = "groupId", required = false) Long groupId,
                            @PathVariable("actuator-id") Long actuatorId,
                            @RequestBody ActuatorNameUpdateRequest request) {
        actuatorClient.updateActuatorName(userId, groupId, actuatorId, request);
    }

    @DeleteMapping("/{actuator-id}")
    @ResponseBody
    public void delete(@SessionAttribute(value = "userId", required = false) Long userId,
                        @SessionAttribute(value = "groupId", required = false) Long groupId,
                        @PathVariable("actuator-id") Long actuatorId) {
        actuatorClient.deleteActuatorById(userId, groupId, actuatorId);
    }

    /** 액추에이터 추가 폼 — locationId는 URL에서 이미 정해지므로 이름·타입만 받는다. */
    public record ActuatorCreateForm(String name, ActuatorType actuatorType) {}
}
