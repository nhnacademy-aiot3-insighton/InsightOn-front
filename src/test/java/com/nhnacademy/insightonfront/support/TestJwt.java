package com.nhnacademy.insightonfront.support;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 테스트용 비서명 JWT 생성기. 프로덕션 코드는 payload(두 번째 세그먼트)만 base64url 디코드해
 * 클레임을 읽으므로 서명은 의미 없는 값으로 채운다.
 */
public final class TestJwt {

    private TestJwt() {
    }

    /** payload JSON 그대로를 담은 JWT 문자열(header.payload.sig)을 만든다. */
    public static String of(String payloadJson) {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return "eyJhbGciOiJub25lIn0." + payload + ".sig";
    }

    /** roles 배열만 담은 accessToken. */
    public static String withRoles(String... roles) {
        StringBuilder sb = new StringBuilder("{\"roles\":[");
        for (int i = 0; i < roles.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(roles[i]).append('"');
        }
        sb.append("]}");
        return of(sb.toString());
    }

    /** sub/name 클레임을 담은 accessToken. */
    public static String withUser(long sub, String name) {
        return of("{\"sub\":" + sub + ",\"name\":\"" + name + "\"}");
    }

    /** exp 클레임(epoch seconds)을 담은 accessToken. */
    public static String withExp(long epochSeconds) {
        return of("{\"exp\":" + epochSeconds + "}");
    }
}
