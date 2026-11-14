package com.unitel.laoads.domain.auth.vsa;

/**
 * Port SSO VSA (Viettel, chuẩn CAS 2.0). Implementation ở laoads-infra ({@code CasVsaClient}).
 */
public interface VsaClient {

    /** true khi {@code laoads.vsa.enabled=true} và đã cấu hình cas-url. */
    boolean isEnabled();

    /** URL trang đăng nhập CAS ({@code casUrl/login?service=...}) hoặc null khi tắt. */
    String loginUrl();

    /** service URL cấu hình sẵn — dùng khi client không gửi {@code service}. */
    String defaultServiceUrl();

    /**
     * Gọi {@code casUrl/serviceValidate}. Trả username VSA khi ticket hợp lệ.
     * Ném LaoadsException UNAUTHORIZED (ticket sai/hết hạn) hoặc VSA_UNAVAILABLE (không gọi được CAS).
     */
    String validateTicket(String ticket, String service);
}
