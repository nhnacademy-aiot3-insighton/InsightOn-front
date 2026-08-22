package com.nhnacademy.insightonfront.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelemetryHeartbeatScheduler {

    private final SseEmitterRegistry sseEmitterRegistry;

    @Scheduled(fixedRate = 5000)
    public void sendHeartbeat() {
        sseEmitterRegistry.broadcastHeartbeat();
    }
}
