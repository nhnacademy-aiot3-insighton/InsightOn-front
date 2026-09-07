package com.nhnacademy.insightonfront.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AccessTokenContext 단위 테스트 — 요청 스코프 accessToken 보관용 ThreadLocal 의
 * set/get/clear 와 스레드 격리를 확인한다.
 */
class AccessTokenContextTest {

    @AfterEach
    void tearDown() {
        AccessTokenContext.clear();
    }

    @Test
    @DisplayName("set 한 토큰을 get 으로 돌려준다")
    void setThenGet() {
        AccessTokenContext.set("access-1");

        assertThat(AccessTokenContext.get()).isEqualTo("access-1");
    }

    @Test
    @DisplayName("clear 하면 null 이 된다")
    void clearRemovesValue() {
        AccessTokenContext.set("access-1");

        AccessTokenContext.clear();

        assertThat(AccessTokenContext.get()).isNull();
    }

    @Test
    @DisplayName("설정하지 않은 상태에서는 null 이다")
    void getWithoutSetIsNull() {
        assertThat(AccessTokenContext.get()).isNull();
    }

    @Test
    @DisplayName("다른 스레드의 값에 영향받지 않는다")
    void isolatedPerThread() throws InterruptedException {
        AccessTokenContext.set("main-thread");
        String[] fromOther = new String[1];

        Thread other = new Thread(() -> {
            fromOther[0] = AccessTokenContext.get();
            AccessTokenContext.set("other-thread");
        });
        other.start();
        other.join();

        assertThat(fromOther[0]).isNull();
        assertThat(AccessTokenContext.get()).isEqualTo("main-thread");
    }
}
