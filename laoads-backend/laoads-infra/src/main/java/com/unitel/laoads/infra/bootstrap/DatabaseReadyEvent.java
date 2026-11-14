package com.unitel.laoads.infra.bootstrap;

/**
 * Phát 1 lần khi DB kết nối được và Flyway migrate xong — các tác vụ cần bảng LADS_* (seed dev)
 * lắng nghe event này thay vì chạy lúc startup (lúc đó DB có thể chưa lên / chưa migrate).
 */
public record DatabaseReadyEvent(int migrationsExecuted, String schemaVersion) {
}
