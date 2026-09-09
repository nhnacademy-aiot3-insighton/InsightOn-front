package com.nhnacademy.insightonfront.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/** 새 accessToken을 현재 응답의 Set-Cookie로 실어 브라우저 쿠키를 갱신한다. */
public final class AccessTokenCookieWriter {

    private AccessTokenCookieWriter() {}

    public static void write(String newAccessToken) {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attrs) {
            HttpServletResponse res = attrs.getResponse();
            if (res != null && !res.isCommitted()) {   // 응답 아직 안 나갔을 때만
                HttpServletRequest req = attrs.getRequest();
                boolean secure = req.isSecure();
                res.addHeader(HttpHeaders.SET_COOKIE,
                        ResponseCookie.from("accessToken", newAccessToken)
                                .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                                .maxAge(Duration.ofMinutes(15))
                                .build().toString());
            }
        }
    }
}
