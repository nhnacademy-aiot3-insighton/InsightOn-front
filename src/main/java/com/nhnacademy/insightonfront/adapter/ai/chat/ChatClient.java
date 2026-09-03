package com.nhnacademy.insightonfront.adapter.ai.chat;

import com.nhnacademy.insightonfront.adapter.ai.chat.dto.ChatMessageResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * userId는 안 넘긴다 — 게이트웨이가 Authorization을 검증해서 X-User-Id로 바꿔 AI 서비스에 넘겨준다.
 */
@FeignClient(name = "insighton-gateway", contextId = "chatClient", url = "${service-url.gateway}")
public interface ChatClient {

    @GetMapping("/api/v1/chat")
    List<ChatMessageResponse> getHistory(@RequestParam("groupId") Long groupId);
}
