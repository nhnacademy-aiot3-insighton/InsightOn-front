package com.nhnacademy.insightonfront.controller;

import com.nhnacademy.insightonfront.adapter.core.sensor.SensorClient;
import com.nhnacademy.insightonfront.telemetry.SseEmitterRegistry;
import feign.FeignException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TelemetrySseController {

    private final SensorClient sensorClient;
    private final SseEmitterRegistry sseEmitterRegistry;

    @GetMapping("/sse/sensors/{sensorId}")
    public SseEmitter subscribe(@CookieValue(value = "accessToken", required = false) String accessToken,
                                 @CookieValue(value = "userId", required = false) Long userId,
                                 @PathVariable Long sensorId,
                                 HttpServletResponse response) throws IOException {

        if (Objects.isNull(accessToken) || Objects.isNull(userId)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return null;
        }

        try {
            sensorClient.getSensor(sensorId);
        } catch (FeignException e) {
            log.warn("[TelemetrySseController] 구독 거부 - userId: {}, sensorId: {}, status: {}",
                    userId, sensorId, e.status());
            response.sendError(HttpStatus.FORBIDDEN.value());
            return null;
        }

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        sseEmitterRegistry.register(sensorId, emitter);
        return emitter;
    }
}
