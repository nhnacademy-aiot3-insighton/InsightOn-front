package com.nhnacademy.insightonfront.handler;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 프론트엔드 전역 예외 처리기 (Global Exception Handler)
 * <p>백엔드(Core/Auth/RuleEngine) 통신 중 FeignException이나 기타 런타임 예외 발생 시
 * 500 흰색 에러 화면 대신, 백엔드의 원본 에러 상태 코드와 메시지를 추출하여
 * 커스텀 error.html 화면으로 안내함.
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(FeignException.class)
    public String handleFeignException(FeignException e, HttpServletRequest request, Model model) {
        int status = e.status() > 0 ? e.status() : HttpStatus.INTERNAL_SERVER_ERROR.value();
        log.warn("[Front Global Handler] FeignException - URI: {}, status: {}, message: {}",
                request.getRequestURI(), status, e.getMessage());

        if (status == 401) {
            return "redirect:/login?expired=1";
        }

        String downstreamMessage = extractDownstreamMessage(e);
        model.addAttribute("status", status);
        model.addAttribute("errorMessage", downstreamMessage);

        return "error";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String handleResponseStatusException(ResponseStatusException e, HttpServletRequest request, Model model) {
        log.warn("[Front Global Handler] ResponseStatusException - URI: {}, status: {}, reason: {}",
                request.getRequestURI(), e.getStatusCode().value(), e.getReason());

        model.addAttribute("status", e.getStatusCode().value());
        model.addAttribute("errorMessage", e.getReason());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneralException(Exception e, HttpServletRequest request, Model model) {
        log.error("[Front Global Handler] Unhandled Exception - URI: {}", request.getRequestURI(), e);

        model.addAttribute("status", 500);
        model.addAttribute("errorMessage", "서비스 이용 중 일시적인 오류가 발생했습니다.");
        return "error";
    }

    private String extractDownstreamMessage(FeignException e) {
        try {
            String bodyStr = e.contentUTF8();
            if (bodyStr != null && !bodyStr.isBlank()) {
                JsonNode jsonNode = objectMapper.readTree(bodyStr);
                if (jsonNode.has("message") && !jsonNode.get("message").isNull()) {
                    return jsonNode.get("message").asString();
                }
                if (jsonNode.has("detail") && !jsonNode.get("detail").isNull()) {
                    return jsonNode.get("detail").asString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
