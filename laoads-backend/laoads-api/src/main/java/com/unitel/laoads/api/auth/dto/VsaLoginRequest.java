package com.unitel.laoads.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code service} = URL đã dùng khi redirect sang CAS login; bỏ trống → dùng laoads.vsa.service-url. */
public record VsaLoginRequest(@NotBlank String ticket, String service) {
}
