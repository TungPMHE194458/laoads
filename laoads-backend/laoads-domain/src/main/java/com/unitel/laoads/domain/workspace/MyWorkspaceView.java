package com.unitel.laoads.domain.workspace;

import java.util.List;

/** 1 dòng của /me.workspaces: workspace + vai trò (null khi SUPER_ADMIN không là thành viên) + quyền hiệu lực. */
public record MyWorkspaceView(Workspace workspace, MemberRole role, List<String> permissions) {
}
