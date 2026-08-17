package com.nhnacademy.insightonfront.adapter.core.weather;

import com.nhnacademy.insightonfront.adapter.core.weather.dto.WeatherDataDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Core의 그룹 기반 날씨/미세먼지 조회. groupId만 넘기면 core가 승인 시 캐싱해둔 그룹 지역 정보로
 * 기상청 격자좌표를 알아서 찾아 처리한다 — 격자좌표를 직접 넘겨야 하는 WeatherController는 프론트에서
 * 좌표를 알 방법이 없어 쓰지 않는다.
 * <p><b>알려진 제약(core 세션 확인)</b>: 그룹 지역 캐시가 없거나 기상청/미세먼지 API 호출이 실패하면
 * 에러 메시지 없는 표준 500이 그대로 온다(GlobalExceptionHandler 미등록) — 호출부에서 반드시
 * try/catch로 감싸고 대체 UI를 보여줘야 한다.
 */
@FeignClient(name = "insighton-gateway", contextId = "weatherClient", url = "${service-url.gateway}")
public interface WeatherClient {

    @GetMapping("/api/v1/weather/group/{groupId}")
    WeatherDataDto getWeatherByGroupId(@PathVariable("groupId") Long groupId,
                                       @RequestParam("baseDate") String baseDate,
                                       @RequestParam("baseTime") String baseTime);
}
