package com.unitel.laoads.api.common;

import com.unitel.laoads.domain.auth.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;

/** Dựng {@link ClientInfo} từ request (IP ưu tiên X-Forwarded-For đầu tiên — sau nginx/LB). */
public final class ClientInfos {

    private ClientInfos() {
    }

    public static ClientInfo from(HttpServletRequest req) {
        if (req == null) {
            return ClientInfo.UNKNOWN;
        }
        return new ClientInfo(clientIp(req), req.getHeader("User-Agent"));
    }

    public static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }
}
