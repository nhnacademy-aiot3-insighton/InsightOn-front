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
import org.springframework.web.bind.annotation.CookieValue;

/**
 * 게이트웨이로 자동 등록된 센서를 조회·이름변경·위치재배치·삭제한다. 센서는 물리 장치가 통신을
 * 시작하면 core가 알아서 만들기 때문에(FR 상 발견/등록 API 없음) "센서 추가" 같은 생성 액션은 없다.
 * groupId는 쿠키에서 읽는다(한 유저 = 한 그룹). userId는 게이트웨이가 Authorization에서 뽑아 쓴다.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/my-group/sensors")
public class SensorController {

    private final SensorClient sensorClient;
    private final SensorAttributeClient sensorAttributeClient;
    private final LocationClient locationClient;

    // 위치 필터 select에 "미배정"을 끼워넣기 위한 sentinel 값 — 실제 locationId(PK)는 항상 양수라 안겹침
    private static final long UNASSIGNED_LOCATION_ID = -1L;

    @GetMapping
    public String list(@CookieValue(value = "userId", required = false) Long userId,
                        @CookieValue(value = "groupId", required = false) Long groupId,
                        @RequestParam(required = false) Long locationId,
                        @RequestParam(required = false) String sensorName,
                        Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }

        // 미배정이면 위치별 검색 대신 core의 미배정 센서 전용 API를 사용
        boolean unassignedOnly = locationId != null && locationId == UNASSIGNED_LOCATION_ID;

        List<SensorResponse> sensors = unassignedOnly
                ? sensorClient.getUnassignedSensors(groupId)
                : sensorClient.search(groupId, null, null, locationId, sensorName);
        List<LocationListResponse> locations = locationClient.getLocationList(groupId);

        model.addAttribute("sensors", sensors);
        model.addAttribute("locations", locations);
        model.addAttribute("selectedLocationId", locationId);
        model.addAttribute("searchQuery", sensorName);
        return "sensor/list";
    }

    @GetMapping("/{sensor-id}")
    public String detail(@CookieValue(value = "userId", required = false) Long userId,
                          @CookieValue(value = "groupId", required = false) Long groupId,
                          @PathVariable("sensor-id") Long sensorId,
                          Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        SensorResponse sensor = sensorClient.getSensor(sensorId);
        List<SensorAttributeResponse> attributes = sensorAttributeClient.getSensorAttribute(sensorId);
        List<LocationListResponse> locations = locationClient.getLocationList(groupId);

        model.addAttribute("sensor", sensor);
        model.addAttribute("attributes", attributes);
        model.addAttribute("locations", locations);
        return "sensor/detail";
    }

    @PutMapping("/{sensor-id}")
    @ResponseBody
    public void update(@PathVariable("sensor-id") Long sensorId,
                        @RequestBody SensorUpdateRequest request) {
        sensorClient.updateSensor(sensorId, request);
    }

    @DeleteMapping("/{sensor-id}")
    @ResponseBody
    public void delete(@PathVariable("sensor-id") Long sensorId) {
        sensorClient.deleteSensor(sensorId);
    }

    @DeleteMapping("/{sensor-id}/attributes/{metric-key}")
    @ResponseBody
    public void deleteAttribute(@PathVariable("sensor-id") Long sensorId,
                                 @PathVariable("metric-key") String metricKey) {
        sensorAttributeClient.deleteSensorAttribute(sensorId, metricKey);
    }
}
