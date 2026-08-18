package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.location.LocationClient;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationListResponse;
import com.nhnacademy.insightonfront.adapter.core.sensor.SensorClient;
import com.nhnacademy.insightonfront.adapter.core.sensor.dto.SensorResponse;
import com.nhnacademy.insightonfront.adapter.core.sensor.dto.SensorUpdateRequest;
import com.nhnacademy.insightonfront.adapter.core.sensorattribute.SensorAttributeClient;
import com.nhnacademy.insightonfront.adapter.core.sensorattribute.dto.SensorAttributeResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * 게이트웨이로 자동 등록된 센서를 조회·이름변경·위치재배치·삭제한다. 센서는 물리 장치가 통신을
 * 시작하면 core가 알아서 만들기 때문에(FR 상 발견/등록 API 없음) "센서 추가" 같은 생성 액션은 없다.
 * userId·groupId는 세션에서 읽는다(한 유저 = 한 그룹).
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/sensors")
public class SensorController {

    private final SensorClient sensorClient;
    private final SensorAttributeClient sensorAttributeClient;
    private final LocationClient locationClient;

    @GetMapping
    public String list(@SessionAttribute(value = "userId", required = false) Long userId,
                        @SessionAttribute(value = "groupId", required = false) Long groupId,
                        @RequestParam(required = false) Long locationId,
                        @RequestParam(required = false) String sensorName,
                        Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        List<SensorResponse> sensors = sensorClient.search(userId, groupId, null, null, locationId, sensorName);
        List<LocationListResponse> locations = locationClient.getLocationList(groupId, userId);

        model.addAttribute("sensors", sensors);
        model.addAttribute("locations", locations);
        model.addAttribute("selectedLocationId", locationId);
        model.addAttribute("searchQuery", sensorName);
        return "sensor/list";
    }

    @GetMapping("/{sensor-id}")
    public String detail(@SessionAttribute(value = "userId", required = false) Long userId,
                          @SessionAttribute(value = "groupId", required = false) Long groupId,
                          @PathVariable("sensor-id") Long sensorId,
                          Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        SensorResponse sensor = sensorClient.getSensor(userId, sensorId);
        List<SensorAttributeResponse> attributes = sensorAttributeClient.getSensorAttribute(userId, sensorId);
        List<LocationListResponse> locations = locationClient.getLocationList(groupId, userId);

        model.addAttribute("sensor", sensor);
        model.addAttribute("attributes", attributes);
        model.addAttribute("locations", locations);
        return "sensor/detail";
    }

    @PutMapping("/{sensor-id}")
    @ResponseBody
    public void update(@SessionAttribute(value = "userId", required = false) Long userId,
                        @PathVariable("sensor-id") Long sensorId,
                        @RequestBody SensorUpdateRequest request) {
        sensorClient.updateSensor(userId, sensorId, request);
    }

    @DeleteMapping("/{sensor-id}")
    @ResponseBody
    public void delete(@SessionAttribute(value = "userId", required = false) Long userId,
                        @PathVariable("sensor-id") Long sensorId) {
        sensorClient.deleteSensor(userId, sensorId);
    }

    @DeleteMapping("/{sensor-id}/attributes/{metric-key}")
    @ResponseBody
    public void deleteAttribute(@SessionAttribute(value = "userId", required = false) Long userId,
                                 @PathVariable("sensor-id") Long sensorId,
                                 @PathVariable("metric-key") String metricKey) {
        sensorAttributeClient.deleteSensorAttribute(userId, sensorId, metricKey);
    }
}
