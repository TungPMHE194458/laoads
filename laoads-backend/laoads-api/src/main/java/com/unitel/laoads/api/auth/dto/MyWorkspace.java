package com.unitel.laoads.api.auth.dto;

import com.unitel.laoads.domain.workspace.MemberRole;
import com.unitel.laoads.domain.workspace.MyWorkspaceView;
import com.unitel.laoads.domain.workspace.WorkspaceStatus;

import java.util.List;

/** {@code role} null khi SUPER_ADMIN không là thành viên; {@code permissions} = toàn bộ với SUPER_ADMIN. */
public record MyWorkspace(Long id, String code, String name, WorkspaceStatus status, MemberRole role,
                          List<String> permissions) {

    public static MyWorkspace from(MyWorkspaceView v) {
        return new MyWorkspace(v.workspace().getId(), v.workspace().getCode(), v.workspace().getName(),
                v.workspace().getStatus(), v.role(), v.permissions());
    }
}
