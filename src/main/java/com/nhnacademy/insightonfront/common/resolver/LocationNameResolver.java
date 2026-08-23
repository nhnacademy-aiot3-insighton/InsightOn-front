package com.nhnacademy.insightonfront.common.resolver;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nhnacademy.insightonfront.adapter.core.location.LocationClient;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationListResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * groupId 기준 위치 목록을 제공한다. Core 호출 결과를 5분간 캐싱해 부하를 줄이고,
 * Core 호출이 실패해도(장애/타임아웃) 예외를 던지지 않고 빈 값을 반환해 화면 렌더링 자체는 계속되게 함
 * (이름 조회부는 Map.getOrDefault(locationId, "대체 텍스트")로 저하 처리).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LocationNameResolver {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final LocationClient locationClient;
    private final Cache<Long, List<LocationListResponse>> cache = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL)
            .build();

    public Map<Long, String> resolve(Long groupId) {
        return getLocations(groupId).stream()
                .collect(Collectors.toMap(LocationListResponse::locationId, LocationListResponse::locationName));
    }

    public List<LocationListResponse> getLocations(Long groupId) {
        List<LocationListResponse> cached = cache.getIfPresent(groupId);
        if (cached != null) {
            return cached;
        }

        try {
            List<LocationListResponse> fresh = locationClient.getLocationList(groupId);
            cache.put(groupId, fresh);
            return fresh;
        } catch (Exception e) {
            log.warn("Core 위치 조회 실패 - groupId:{}", groupId, e);
            return List.of();
        }
    }

    /** 위치 추가/수정/삭제 직후 호출 - 다음 조회 때 캐시된 옛 목록 대신 Core에서 새로 받아오게 한다. */
    public void invalidate(Long groupId) {
        cache.invalidate(groupId);
    }
}
