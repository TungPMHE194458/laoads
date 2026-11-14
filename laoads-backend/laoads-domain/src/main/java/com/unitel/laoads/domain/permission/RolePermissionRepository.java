package com.unitel.laoads.domain.permission;

import com.unitel.laoads.domain.workspace.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermission.Key> {

    List<RolePermission> findByRoleCode(MemberRole roleCode);

    /** clearAutomatically: bắt buộc để save() sau đó INSERT lại được cùng PK trong cùng transaction. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RolePermission r WHERE r.roleCode = :role")
    int deleteByRole(@Param("role") MemberRole role);
}
