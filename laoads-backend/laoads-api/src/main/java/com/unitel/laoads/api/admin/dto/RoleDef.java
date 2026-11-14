package com.unitel.laoads.api.admin.dto;

import com.unitel.laoads.domain.workspace.MemberRole;

import java.util.List;

public record RoleDef(MemberRole code, String name, List<String> permissions) {
}
