package com.nhnacademy.insightonfront;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class InsightonFrontApplicationTests {

    // 실제 Redis 연결을 시도하는 빈이라 컨텍스트 로딩 테스트에선 목으로 대체함 -
    // 운영/개발 환경에서는 이 목이 적용되지 않고 실제 빈이 그대로 동작함
    @MockitoBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Test
    void contextLoads() {
    }

}
