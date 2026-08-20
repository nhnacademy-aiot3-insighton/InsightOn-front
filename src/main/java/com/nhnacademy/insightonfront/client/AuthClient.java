package com.nhnacademy.insightonfront.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 프론트 서버 → 게이트웨이(→ 인증 서버) 로그인 호출.
 * name = 게이트웨이 서비스 ID. 게이트웨이가 /api/v1/auth/** 를 auth 로 라우팅한다.
 */
@FeignClient(name = "insighton-gateway", contextId = "authClient",  url = "${service-url.gateway}")
public interface AuthClient {

    @PostMapping(
            value = "/api/v1/auth/login",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<String> login(@RequestBody LoginBody body);

    /** 인증 서버 UserLoginRequest(email, password) 와 필드명이 같아야 한다. */
    record LoginBody(String email, String password) {}
}