package com.nhnacademy.insightonfront.telemetry;

import com.nhnacademy.insightonfront.telemetry.dto.TelemetryEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetrySseRedisBridge implements MessageListener {

    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            TelemetryEventMessage event = objectMapper.readValue(message.getBody(), TelemetryEventMessage.class);
            sseEmitterRegistry.broadcast(event.sensorId(), event);
        } catch (Exception e) {
            log.warn("텔레메트리 SSE 브릿지 처리 실패", e);
        }
    }
}
