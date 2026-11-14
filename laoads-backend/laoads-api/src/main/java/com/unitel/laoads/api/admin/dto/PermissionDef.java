package com.unitel.laoads.api.admin.dto;

import com.unitel.laoads.domain.permission.PermissionGroup;

public record PermissionDef(String code, PermissionGroup group, String description) {
}
