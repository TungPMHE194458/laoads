package com.unitel.laoads.web.filter;

import com.unitel.laoads.api.common.WorkspaceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Đọc header X-Workspace-Id vào {@link WorkspaceContext} (request-scoped). Không chặn request — việc bắt buộc
 * header do {@code @perm} quyết định theo từng endpoint. Chạy sau Security chain (order mặc định), khi
 * RequestContextFilter đã bind request scope.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceHeaderFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "workspaceId";

    private final WorkspaceContext workspaceContext;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String raw = request.getHeader(WorkspaceContext.HEADER);
        workspaceContext.init(raw);
        workspaceContext.workspaceId().ifPresent(id -> MDC.put(MDC_KEY, String.valueOf(id)));
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
