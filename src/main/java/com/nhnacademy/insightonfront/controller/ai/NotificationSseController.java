package com.nhnacademy.insightonfront.controller.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Controller
public class NotificationSseController {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${service-url.gateway}")
    private String gatewayUrl;

    @GetMapping("/groups/notifications/stream")
    public SseEmitter stream(@CookieValue(value = "accessToken", required = false) String accessToken,
                             @CookieValue(value = "groupId", required = false) Long groupId) {

        if(accessToken == null || groupId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(gatewayUrl + "/api/v1/dashboard-notifications/stream?groupId=" + groupId))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture<Void> upstream = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> relay(emitter, response.body()))
                .exceptionally(e -> {
                    log.warn("[NotificationSseController] 알림 스트리밍 릴레이 실패", e);
                    emitter.completeWithError(e);
                    return null;
                });

        emitter.onCompletion(() -> upstream.cancel(true));
        emitter.onTimeout(() -> upstream.cancel(true));

        return emitter;
    }

    private void relay(SseEmitter emitter, Stream<String> lines) {
        try (lines) {
            lines.filter(line -> line.startsWith("data:"))
                    .map(this::stripDataPrefix)
                    .forEach(token -> {
                        try {
                            emitter.send(token);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private String stripDataPrefix(String line) {
        String value = line.substring(5);
        return value.startsWith(" ") ? value.substring(1) : value;
    }
}
