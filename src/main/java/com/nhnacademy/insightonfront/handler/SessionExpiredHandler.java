package com.nhnacademy.insightonfront.handler;

import com.nhnacademy.insightonfront.adapter.auth.auth.exception.SessionExpiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class SessionExpiredHandler {

    // 예외 발생시 쿠키 무효화
    @ExceptionHandler(SessionExpiredException.class)
    public String handleSessionExpired(HttpServletRequest req, HttpServletResponse res) {
        boolean secure = req.isSecure();
        expireCookie(res, "accessToken", secure);
        expireCookie(res, "refreshToken", secure);
        expireCookie(res, "userId", secure);
        expireCookie(res, "userName", secure);
        expireCookie(res, "groupId", secure);
        return "redirect:/login?expired=1";
    }

    private void expireCookie(HttpServletResponse res, String name, boolean secure) {
        res.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(name, "")
                        .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                        .maxAge(0)
                        .build().toString());
    }
}