package com.nhnacademy.insightonfront.adapter.auth.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nhnacademy.insightonfront.adapter.auth.auth.dto.TokenRefreshResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * refresh를 userId별로 한 번만 수행하고 결과를 잠깐 공유(single-flight)한다.
 * Caffeine의 get(key, function)이 "같은 userId면 한 번만 실행"을 보장하므로
 * 별도 락 관리 없이 동시성·병목·메모리 정리가 모두 처리된다.
 */
@Slf4j
@Component
public class RefreshCoordinator {

    private static final long WINDOW_MS = 10000;   // 10초 내 갱신분은 재사용

    // 토큰 캐시 — 10초 후 자동 만료. 같은 키 동시 요청은 Caffeine이 한 번만 실행.
    private final Cache<Long, String> tokenCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMillis(WINDOW_MS))
            .build();

    private final ObjectProvider<AuthClient> authClientProvider;

    public RefreshCoordinator(ObjectProvider<AuthClient> authClientProvider) {
        this.authClientProvider = authClientProvider;
    }

    /** @return 새 accessToken, 실패 시 null */
    public String getRefreshedToken(Long userId, String refreshToken) {
        // 같은 userId로 동시에 불려도 doRefresh는 한 번만 실행됨 (Caffeine 보장)
        // 나머지 스레드는 그 결과를 기다렸다 재사용. 다른 userId는 독립.
        return tokenCache.get(userId, key -> doRefresh(key, refreshToken));
    }

    /** 실제 refresh 호출. 실패 시 null 반환(캐싱 안 됨 → 다음 요청이 재시도). */
    private String doRefresh(Long userId, String refreshToken) {
        try {
            ResponseEntity<TokenRefreshResponse> res =
                    authClientProvider.getObject().refresh("refreshToken=" + refreshToken);
            TokenRefreshResponse body = res.getBody();
            if (body == null || body.accessToken() == null || body.accessToken().isBlank()) {
                log.warn("[Auth] refresh 응답에 accessToken 없음 (userId={})", userId);
                return null;
            }
            log.info("[Auth] refresh 성공 (userId={})", userId);
            return body.accessToken();
        } catch (Exception e) {
            log.warn("[Auth] refresh 실패 (userId={})", userId, e);
            return null;
        }
    }
}