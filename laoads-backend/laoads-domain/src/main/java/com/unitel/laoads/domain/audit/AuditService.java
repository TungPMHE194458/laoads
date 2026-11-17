package com.unitel.laoads.domain.audit;

import com.unitel.laoads.domain.common.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;

    /** actor tìm "chứa" (không phân biệt hoa thường); actionType/entityType so khớp chính xác. */
    @Transactional(readOnly = true)
    public Page<AuditLog> search(AuditFilter f, Pageable pageable) {
        return repo.search(Texts.likePattern(f.actor()),
                Texts.trimToNull(f.actionType()),
                Texts.trimToNull(f.entityType()),
                f.workspaceId(), f.from(), f.to(), Texts.trimToNull(f.result()), pageable);
    }
}
