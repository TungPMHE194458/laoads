package com.unitel.laoads.domain.common;

/**
 * Tên cache Caffeine — đặt ở domain để service dùng trong @Cacheable/@CacheEvict
 * mà không phụ thuộc ngược sang laoads-infra (CacheConfig đăng ký các tên này).
 */
public final class CacheNames {

    /** role (OWNER/ADMIN/MEMBER) → danh sách permission code. Evict khi SUPER_ADMIN sửa ma trận. */
    public static final String ROLE_PERMISSIONS = "auth.role-permissions";

    private CacheNames() {
    }
}
