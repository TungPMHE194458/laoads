package com.unitel.laoads.domain.permission;

import com.unitel.laoads.domain.common.CacheNames;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.workspace.MemberRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ma trận role → permission. Đọc qua cache Caffeine {@link CacheNames#ROLE_PERMISSIONS}
 * (mỗi request thuộc workspace đều tra), evict toàn bộ khi SUPER_ADMIN sửa ma trận.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RolePermissionService {

    private final RolePermissionRepository repo;

    /** Danh sách permission code (thứ tự theo enum {@link Permission}) — list bất biến, an toàn để cache. */
    @Cacheable(cacheNames = CacheNames.ROLE_PERMISSIONS)
    @Transactional(readOnly = true)
    public List<String> permissionsOf(MemberRole role) {
        Set<String> codes = new java.util.HashSet<>();
        for (RolePermission rp : repo.findByRoleCode(role)) {
            codes.add(rp.getPermissionCode());
        }
        return orderByCatalog(codes);
    }

    /** Thay toàn bộ permission của 1 role. Permission không có trong danh mục → 400 VALIDATION_ERROR. */
    @CacheEvict(cacheNames = CacheNames.ROLE_PERMISSIONS, allEntries = true)
    @Transactional
    public List<String> replace(MemberRole role, Collection<String> permissionCodes, String actor) {
        Set<String> wanted = new LinkedHashSet<>();
        List<FieldViolation> errors = new ArrayList<>();
        int i = 0;
        for (String code : permissionCodes) {
            if (Permission.fromCode(code).isEmpty()) {
                errors.add(new FieldViolation("permissions[" + i + "]", "error.validation.unknownPermission",
                        "Quyền không hợp lệ: " + code, code));
            } else {
                wanted.add(code);
            }
            i++;
        }
        if (!errors.isEmpty()) {
            throw LaoadsException.validation(errors);
        }
        repo.deleteByRole(role);
        Instant now = Instant.now();
        List<RolePermission> rows = wanted.stream()
                .map(code -> new RolePermission(role, code, now, actor))
                .toList();
        repo.saveAll(rows);
        log.info("[PERMISSION] {} cập nhật quyền role {} → {}", actor, role, wanted);
        return orderByCatalog(wanted);
    }

    private static List<String> orderByCatalog(Collection<String> codes) {
        return codes.stream()
                .filter(c -> Permission.fromCode(c).isPresent())
                .sorted(Comparator.comparingInt(c -> Permission.valueOf(c).ordinal()))
                .toList();
    }
}
