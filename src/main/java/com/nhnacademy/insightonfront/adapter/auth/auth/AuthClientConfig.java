package com.nhnacademy.insightonfront.adapter.auth.auth;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * AuthClient 전용 설정.
 * ★ @Configuration 붙이지 말 것 — 붙이면 전역 설정이 되어 refresh 자동화가 깨짐.
 * refresh 호출이 401을 받아도 다시 refresh하지 않도록 기본 ErrorDecoder만 사용.
 */
public class AuthClientConfig {

    @Bean
    public ErrorDecoder authErrorDecoder() {
        return new ErrorDecoder.Default();
    }
}
