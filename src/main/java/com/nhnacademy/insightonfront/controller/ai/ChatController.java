package com.nhnacademy.insightonfront.controller.ai;

import com.nhnacademy.insightonfront.adapter.ai.chat.ChatClient;
import com.nhnacademy.insightonfront.adapter.ai.chat.dto.ChatMessageRequest;
import com.nhnacademy.insightonfront.adapter.ai.chat.dto.ChatMessageResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * 챗봇 위젯이 부르는 창구. accessToken이 httpOnly 쿠키라 브라우저 JS가 직접 Authorization
 * 헤더를 못 붙이므로, 여기서 쿠키를 읽어 Gateway의 /api/v1/chat(SSE)을 대신 호출하고 그대로
 * 릴레이한다. Feign은 응답을 버퍼링해서 토큰 단위 스트리밍에 안 맞아 JDK 내장 HttpClient를 쓴다.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${service-url.gateway}")
    private String gatewayUrl;

    /**
     * 이전 대화 이력 조회. 스트리밍이 필요 없는 단순 JSON 응답이라 chat()과 달리 Feign(ChatClient)을 쓴다
     * - Authorization은 AuthorizationRequestInterceptor가 accessToken 쿠키에서 자동으로 붙여준다.
     */
    @GetMapping("/my-group/chat")
    @ResponseBody
    public List<ChatMessageResponse> history(@CookieValue(value = "groupId", required = false) Long groupId) {
        if (groupId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return chatClient.getHistory(groupId);
    }

    @PostMapping("/my-group/chat")
    public SseEmitter chat(@CookieValue(value = "accessToken", required = false) String accessToken,
                           @CookieValue(value = "groupId", required = false) Long groupId,
                           @RequestParam(required = false) Long locationId,
                           @RequestBody ChatMessageRequest request) {
        if (accessToken == null || groupId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String url = gatewayUrl + "/api/v1/chat?groupId=" + groupId
                + (locationId != null ? "&locationId=" + locationId : "");

        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        SseEmitter emitter = new SseEmitter(0L);
        httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> relay(emitter, response.body()))
                .exceptionally(e -> {
                    log.warn("[ChatController] AI 스트리밍 릴레이 실패", e);
                    emitter.completeWithError(e);
                    return null;
                });

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

    /**
     * "data:" 접두사만 떼고 SSE 스펙대로 그 뒤 공백 한 칸만 제거한다 - .trim()을 쓰면 토큰
     * 자체가 줄바꿈이나 공백일 때(문장 사이 개행 등) 그 내용까지 지워져서 응답이 한 줄로 뭉개진다.
     */
    private String stripDataPrefix(String line) {
        String value = line.substring(5);
        return value.startsWith(" ") ? value.substring(1) : value;
    }
}
