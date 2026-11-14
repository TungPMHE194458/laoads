package com.unitel.laoads.api.admin.dto;

import com.unitel.laoads.domain.user.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull UserStatus status) {
}
