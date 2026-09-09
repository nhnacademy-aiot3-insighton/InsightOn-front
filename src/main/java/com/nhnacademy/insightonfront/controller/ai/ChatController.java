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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<SseEmitter> chat(@CookieValue(value = "accessToken", required = false) String accessToken,
                           @CookieValue(value = "groupId", required = false) Long groupId,
                           @RequestParam(required = false) Long locationId,
                           @RequestBody ChatMessageRequest request) {
        if (accessToken == null || groupId == null) {
            // AccessTokenPreloadFilter가 갱신을 시도했는데도 이 시점에 accessToken이 없다는 뜻 -
            // AI/Gateway를 호출하기도 전에 여기서 즉시 401로 끝난다. "챗봇이 응답 없이 바로
            // 실패한다"는 증상의 원인이 라우팅/AI 쪽이 아니라 여기였는지 로그로 바로 확인 가능하게.
            log.warn("[ChatController] 챗봇 요청 인증 정보 없음 - accessToken={}, groupId={}",
                    accessToken != null, groupId != null);
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
        CompletableFuture<Void> upstream = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> relay(emitter, response.body()))
                .exceptionally(e -> {
                    log.warn("[ChatController] AI 스트리밍 릴레이 실패", e);
                    emitter.completeWithError(e);
                    return null;
                });

        // 브라우저가 연결을 끊었는데 업스트림(Gateway) 요청은 계속 살아있으면 AI가 이미 버려진
        // 요청을 위해 계속 일하는 리소스 누수가 된다 - NotificationSseController와 동일하게 정리.
        emitter.onCompletion(() -> upstream.cancel(true));
        emitter.onTimeout(() -> upstream.cancel(true));

        // Cloudflare 등 앞단 프록시가 text/event-stream 응답을 버퍼링해 하트비트를 보내도 실시간으로
        // 안 흘려보내는 경우가 흔하다 - X-Accel-Buffering: no로 버퍼링을 끄고 no-cache로 캐시 취급도 막는다.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    /**
     * AI 백엔드는 도구 호출로 첫 토큰까지 수십 초 걸릴 수 있는 구간에 SSE 하트비트 주석(":"로 시작)을
     * 보낸다. 예전엔 "data:"가 아니라고 걸러버려서, 그 하트비트가 여기서 죽고 브라우저까지 전혀
     * 전달되지 않았다 - 그 사이 브라우저/중간 프록시가 진짜 침묵으로 보고 연결을 끊어버려도 AI는
     * 뒤에서 계속 처리해 채팅 이력엔 저장되지만 사용자는 아무 응답도 못 보는 원인이었다. data:는
     * 내용을 릴레이하고, 하트비트는 SSE 주석 그대로(마크다운 렌더링에 안 섞이게) 릴레이해 연결이
     * 계속 "살아있다"는 신호가 브라우저까지 도달하게 한다.
     */
    private void relay(SseEmitter emitter, Stream<String> lines) {
        try (lines) {
            lines.forEach(line -> {
                try {
                    if (line.startsWith("data:")) {
                        emitter.send(stripDataPrefix(line));
                    } else if (line.startsWith(":")) {
                        emitter.send(SseEmitter.event().comment(line.substring(1)));
                    }
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
