package com.nhnacademy.insightonfront.adapter.core.region;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Core의 행정구역 레지스트리 API를 Gateway 경유로 호출한다 — 그룹 신청서의 시/도·시/군/구
 * 드롭다운을 채우는 데 쓴다. state+city 조합은 이 레지스트리 기준으로 서버에서 검증되므로
 * (없는 조합이면 400) 자유 입력 대신 이 목록에서만 고르게 한다.
 */
@FeignClient(name = "insighton-gateway", contextId = "regionClient", url = "${service-url.gateway}")
public interface RegionClient {

    @GetMapping("/api/v1/regions/states")
    List<String> getStates();

    @GetMapping("/api/v1/regions/cities")
    List<String> getCities(@RequestParam("state") String state);
}
