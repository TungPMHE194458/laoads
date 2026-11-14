package com.unitel.laoads.api.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu endpoint thuộc workspace (🏢 trong contract) — chỉ để OpenAPI sinh header
 * {@code X-Workspace-Id}. Việc kiểm tra thật do {@code @perm} trong @PreAuthorize.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface WorkspaceScoped {

    /** false: header tuỳ chọn (vd /audit-logs — SUPER_ADMIN không cần). */
    boolean required() default true;
}
