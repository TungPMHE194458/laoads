package com.unitel.laoads.domain.workspace;

/** REVOKED = đã bị gỡ khỏi workspace (soft-delete, thêm lại sẽ kích hoạt lại bản ghi). */
public enum MemberStatus {
    ACTIVE,
    REVOKED
}
