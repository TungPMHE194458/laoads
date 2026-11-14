package com.unitel.laoads.domain.auth;

/** Thông tin client lưu kèm refresh token (IP + User-Agent, đã cắt độ dài). */
public record ClientInfo(String ip, String userAgent) {

    public static final ClientInfo UNKNOWN = new ClientInfo(null, null);

    public ClientInfo {
        ip = truncate(ip, 64);
        userAgent = truncate(userAgent, 500);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
