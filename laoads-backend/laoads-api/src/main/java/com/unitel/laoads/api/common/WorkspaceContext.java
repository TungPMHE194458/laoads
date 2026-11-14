package com.unitel.laoads.api.common;

import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;

/**
 * Workspace của request hiện tại, do {@code WorkspaceHeaderFilter} (laoads-web) đọc từ header
 * {@value #HEADER}. Request-scoped (proxy) để singleton như {@code @perm} inject trực tiếp.
 */
@Component
@RequestScope
public class WorkspaceContext {

    public static final String HEADER = "X-Workspace-Id";

    private String rawValue;
    private Long workspaceId;

    /** Gọi bởi filter. Header không phải số → giữ raw để báo INVALID_PARAM khi endpoint cần. */
    public void init(String headerValue) {
        this.rawValue = headerValue == null || headerValue.isBlank() ? null : headerValue.trim();
        this.workspaceId = null;
        if (rawValue != null) {
            try {
                long v = Long.parseLong(rawValue);
                this.workspaceId = v > 0 ? v : null;
            } catch (NumberFormatException ignored) {
                this.workspaceId = null;
            }
        }
    }

    public boolean isPresent() {
        return rawValue != null;
    }

    public Optional<Long> workspaceId() {
        return Optional.ofNullable(workspaceId);
    }

    /** Thiếu header → 400 WORKSPACE_REQUIRED; header sai định dạng → 400 INVALID_PARAM. */
    public long requireWorkspaceId() {
        if (rawValue == null) {
            throw new LaoadsException(ErrorCode.WORKSPACE_REQUIRED, "Thiếu header X-Workspace-Id");
        }
        if (workspaceId == null) {
            throw LaoadsException.withKey(ErrorCode.INVALID_PARAM, "error.INVALID_PARAM.workspaceHeader",
                    "Header X-Workspace-Id không hợp lệ: " + rawValue, rawValue);
        }
        return workspaceId;
    }
}
