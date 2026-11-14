package com.unitel.laoads.api.auth.dto;

import java.util.List;

public record MeResponse(UserDto user, List<MyWorkspace> workspaces) {
}
