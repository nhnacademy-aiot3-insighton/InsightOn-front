package com.nhnacademy.insightonfront.config;

import com.nhnacademy.insightonfront.adapter.auth.auth.RefreshCoordinator;
import com.nhnacademy.insightonfront.adapter.auth.auth.TokenRefreshErrorDecoder;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignGlobalConfig {

    @Bean
    public ErrorDecoder errorDecoder(ObjectProvider<RefreshCoordinator> coordinatorProvider) {
        return new TokenRefreshErrorDecoder(coordinatorProvider);
    }

    /** RetryableException을 받아 원요청을 딱 1회 재시도 (지연 0, 최대 시도 2회) */
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(0, 0, 2);
    }
}
