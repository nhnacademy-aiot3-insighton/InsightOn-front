package com.nhnacademy.insightonfront.common.resolver;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nhnacademy.insightonfront.adapter.core.location.LocationClient;
import com.nhnacademy.insightonfront.adapter.core.location.dto.LocationListResponse;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * groupId 기준 locationId → locationName 매핑을 제공한다. Core 호출 결과를 5분간 캐싱해 부하를 줄이고,
 * Core 호출이 실패해도(장애/타임아웃) 예외를 던지지 않고 빈 Map을 반환해 화면 렌더링 자체는 계속되게 함
 * * (호출부는 Map.getOrDefault(locationId, "대체 텍스트")로 저하 처리).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LocationNameResolver {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final LocationClient locationClient;
    private final Cache<Long, Map<Long, String>> cache = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL)
            .build();

    public Map<Long, String> resolve(Long groupId, Long userId) {
        Map<Long, String> cached = cache.getIfPresent(groupId);
        if (cached != null) {
            return cached;
        }

        try {
            Map<Long, String> fresh = locationClient.getLocationList(groupId, userId).stream()
                    .collect(Collectors.toMap(LocationListResponse::locationId, LocationListResponse::locationName));
            cache.put(groupId, fresh);
            return fresh;
        } catch (Exception e) {
            log.warn("Core 위치 조회 실패, 이름 없이 진행 - groupId:{}", groupId, e);
            return Map.of();
        }
    }
}
