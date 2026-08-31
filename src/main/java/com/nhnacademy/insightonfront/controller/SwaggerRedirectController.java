package com.nhnacademy.insightonfront.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class SwaggerRedirectController {

    @Value("${service-url.gateway}")
    private String publicGatewayUrl;

    @GetMapping("/swagger")
    public String redirectToSwagger() {
        return "redirect:" + publicGatewayUrl + "/api/swagger";
    }
}