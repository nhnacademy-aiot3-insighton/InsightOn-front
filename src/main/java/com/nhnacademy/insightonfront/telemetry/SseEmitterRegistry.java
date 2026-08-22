package com.nhnacademy.insightonfront.telemetry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SseEmitterRegistry {

    private final Map<Long, List<SseEmitter>> emittersBySensorId = new ConcurrentHashMap<>();

    public void register(Long sensorId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersBySensorId.computeIfAbsent(sensorId, id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> remove(sensorId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            remove(sensorId, emitter);
        });
        emitter.onError(e -> remove(sensorId, emitter));
    }

    public void broadcast(Long sensorId, Object event) {
        List<SseEmitter> emitters = emittersBySensorId.get(sensorId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("telemetry").data(event));
            } catch (IOException e) {
                remove(sensorId, emitter);
            }
        }
    }

    public void broadcastHeartbeat() {
        emittersBySensorId.forEach((sensorId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException e) {
                    remove(sensorId, emitter);
                }
            }
        });
    }

    private void remove(Long sensorId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersBySensorId.get(sensorId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersBySensorId.remove(sensorId);
            }
        }
    }
}
