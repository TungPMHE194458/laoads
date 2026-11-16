package com.unitel.laoads.domain.common;

/**
 * Mã lỗi nghiệp vụ = giá trị {@code statusCode} trong envelope lỗi (xem docs/API_CONTRACT_V1.md §1).
 * Mỗi mã gắn sẵn HTTP status. Thông báo i18n tra theo key {@code error.<MÃ>} trong messages_*.properties.
 *
 * <p>Domain không phụ thuộc spring-web nên giữ status dạng int; GlobalExceptionHandler map sang HTTP.
 */
public enum ErrorCode {

    // ===== Chuẩn (contract §1) =====
    UNAUTHORIZED(401),
    TOKEN_EXPIRED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    VALIDATION_ERROR(400),
    INVALID_PARAM(400),
    BAD_REQUEST_BODY(400),
    CONFLICT(409),
    INTERNAL_ERROR(500),

    // ===== Bổ sung (không có trong contract v1 — xem báo cáo deviation) =====
    METHOD_NOT_ALLOWED(405),
    DB_UNAVAILABLE(503),
    USER_LOCKED(403),
    VSA_UNAVAILABLE(503),
    EXPORT_TOO_LARGE(400),

    // ===== Workspace / thành viên (contract §1, §4, §5) =====
    WORKSPACE_REQUIRED(400),
    WORKSPACE_CODE_DUPLICATE(409),
    USER_NOT_FOUND(404),
    MEMBER_DUPLICATE(409),
    OWNER_ONLY(403),
    CANNOT_CHANGE_SELF(400),
    CANNOT_REMOVE_OWNER(400),
    CANNOT_REMOVE_SELF(400),

    // ===== Auth / user (contract §3, §6) =====
    USERNAME_DUPLICATE(409),
    CANNOT_LOCK_SELF(400),
    WRONG_PASSWORD(400),
    VSA_DISABLED(400),
    VSA_USER_NOT_PROVISIONED(403),

    // ===== Bảng giá (contract §7) =====
    PRICE_QUOTE_NOT_DRAFT(409),

    // ===== Đợt 2–3: cấu hình gửi tin, tệp KH, chiến dịch, API sự kiện =====
    SENDER_CODE_DUPLICATE(409),
    SENDER_IN_USE(409),
    GROUP_NAME_DUPLICATE(409),
    GROUP_IN_USE(409),
    SEGMENT_NAME_DUPLICATE(409),
    SEGMENT_NOT_READY(409),
    SEGMENT_IN_USE(409),
    CAMPAIGN_LOCKED(409),
    INVALID_TRANSITION(409),
    FILE_INVALID(400),
    API_KEY_INVALID(401),

    // ===== Audience API (OCS / BCCS3) =====
    AUDIENCE_NOT_CONFIGURED(400),
    AUDIENCE_UNAVAILABLE(503);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }

    /** Key mặc định trong messages_*.properties. */
    public String messageKey() {
        return "error." + name();
    }
}
