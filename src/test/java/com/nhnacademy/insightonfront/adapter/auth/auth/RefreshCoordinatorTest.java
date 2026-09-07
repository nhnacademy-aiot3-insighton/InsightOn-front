package com.nhnacademy.insightonfront.adapter.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonfront.adapter.auth.auth.dto.TokenRefreshResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;

/**
 * RefreshCoordinator 단위 테스트 — userId 별 single-flight(Caffeine)로 refresh 를
 * 한 번만 수행하고, 실패한 결과는 캐싱하지 않아 다음 요청이 재시도하는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class RefreshCoordinatorTest {

    @Mock
    private ObjectProvider<AuthClient> authClientProvider;
    @Mock
    private AuthClient authClient;

    private RefreshCoordinator coordinator;

    @BeforeEach
    void setUp() {
        when(authClientProvider.getObject()).thenReturn(authClient);
        coordinator = new RefreshCoordinator(authClientProvider);
    }

    @Test
    @DisplayName("refresh 성공 시 새 accessToken 을 반환하고 refreshToken 을 쿠키 헤더로 전달한다")
    void returnsNewAccessToken() {
        when(authClient.refresh(anyString())).thenReturn(ResponseEntity.ok(new TokenRefreshResponse("NEW-AT")));

        String token = coordinator.getRefreshedToken(1L, "RT-1");

        assertThat(token).isEqualTo("NEW-AT");
        verify(authClient).refresh("refreshToken=RT-1");
    }

    @Test
    @DisplayName("같은 userId 로 연달아 불러도 refresh 는 한 번만 실행된다")
    void singleFlightPerUserId() {
        when(authClient.refresh(anyString())).thenReturn(ResponseEntity.ok(new TokenRefreshResponse("NEW-AT")));

        String first = coordinator.getRefreshedToken(7L, "RT");
        String second = coordinator.getRefreshedToken(7L, "RT");

        assertThat(first).isEqualTo("NEW-AT");
        assertThat(second).isEqualTo("NEW-AT");
        verify(authClient, times(1)).refresh(anyString());
    }

    @Test
    @DisplayName("다른 userId 는 각각 refresh 한다")
    void independentPerUserId() {
        when(authClient.refresh(anyString())).thenReturn(ResponseEntity.ok(new TokenRefreshResponse("NEW-AT")));

        coordinator.getRefreshedToken(1L, "RT");
        coordinator.getRefreshedToken(2L, "RT");

        verify(authClient, times(2)).refresh(anyString());
    }

    @Test
    @DisplayName("응답 바디가 없으면 null 이고 캐싱하지 않는다")
    void nullBodyIsNotCached() {
        when(authClient.refresh(anyString())).thenReturn(ResponseEntity.ok().build());

        assertThat(coordinator.getRefreshedToken(1L, "RT")).isNull();
        assertThat(coordinator.getRefreshedToken(1L, "RT")).isNull();
        verify(authClient, times(2)).refresh(anyString());
    }

    @Test
    @DisplayName("accessToken 이 공백이면 null")
    void blankAccessTokenIsNull() {
        when(authClient.refresh(anyString())).thenReturn(ResponseEntity.ok(new TokenRefreshResponse("   ")));

        assertThat(coordinator.getRefreshedToken(1L, "RT")).isNull();
    }

    @Test
    @DisplayName("refresh 중 예외가 나면 null 이고 캐싱하지 않는다")
    void exceptionIsNotCached() {
        when(authClient.refresh(anyString())).thenThrow(new RuntimeException("auth down"));

        assertThat(coordinator.getRefreshedToken(1L, "RT")).isNull();
        assertThat(coordinator.getRefreshedToken(1L, "RT")).isNull();
        verify(authClient, times(2)).refresh(anyString());
    }
}
