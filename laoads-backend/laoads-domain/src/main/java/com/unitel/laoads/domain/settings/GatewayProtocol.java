package com.unitel.laoads.domain.settings;

/** Giao thức kết nối gateway của một đầu số. API dùng thêm giá trị "NONE" (= null trong DB, chưa cấu hình). */
public enum GatewayProtocol {
    /** SMPP 3.4: host + port + system_id / password / system_type, bind_transceiver. */
    SMPP,
    /** HTTP API của nhà cung cấp: URL + tài khoản (Basic auth) hoặc token. */
    HTTP
}
