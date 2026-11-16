package com.unitel.laoads.domain.settings;

/** Kết quả kiểm tra kết nối gateway; FE dịch theo mã, {@code detail} là thông tin kỹ thuật kèm theo. */
public enum ConnectionTestCode {
    OK,
    /** Hết thời gian chờ kết nối / phản hồi. */
    TIMEOUT,
    /** Máy đích từ chối kết nối (port đóng). */
    REFUSED,
    /** Không phân giải được host. */
    UNKNOWN_HOST,
    /** Sai tài khoản / mật khẩu (SMPP ESME_RINVPASWD / ESME_RINVSYSID, HTTP 401 / 403). */
    AUTH_FAILED,
    /** Gateway từ chối bind / trả lỗi HTTP khác. */
    REJECTED,
    /** Phản hồi không đúng giao thức. */
    PROTOCOL_ERROR,
    ERROR
}
