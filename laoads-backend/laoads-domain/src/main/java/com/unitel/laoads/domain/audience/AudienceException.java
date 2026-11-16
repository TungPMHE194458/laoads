package com.unitel.laoads.domain.audience;

/** Lỗi khi gọi Audience API; {@link AudienceService} đổi sang LaoadsException (400 / 503) theo {@link Kind}. */
public class AudienceException extends RuntimeException {

    public enum Kind {
        /** Chưa cấu hình URL / API key cho nguồn. */
        NOT_CONFIGURED,
        /** Service trả 401 / 403 — API key sai. */
        UNAUTHORIZED,
        /** Service trả 400 / 404 / 422 — request không hợp lệ (bảng/cột không tồn tại…). */
        BAD_REQUEST,
        /** Không kết nối được, hết thời gian chờ, service lỗi 5xx. */
        UNAVAILABLE,
        /** Phản hồi sai định dạng (JSON / NDJSON hỏng). */
        PROTOCOL
    }

    private final Kind kind;

    public AudienceException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AudienceException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
