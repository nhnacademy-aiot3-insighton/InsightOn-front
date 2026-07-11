package com.nhnacademy.insightonfront.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequiredArgsConstructor
public class TestGatewayController {

    @LoadBalanced
    private final RestClient.Builder restClient;

    @GetMapping("/test-gateway")
    public String callGateway() {
        return restClient.build()
                .get()
                .uri("http://insighton-gateway/dummy/hello")
                .retrieve()
                .body(String.class);
    }
}
