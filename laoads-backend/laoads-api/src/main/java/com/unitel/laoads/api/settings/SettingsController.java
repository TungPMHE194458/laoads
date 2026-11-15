package com.unitel.laoads.api.settings;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.settings.NotificationSettings;
import com.unitel.laoads.domain.settings.SenderKind;
import com.unitel.laoads.domain.settings.SenderNumber;
import com.unitel.laoads.domain.settings.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Cấu hình gửi tin: danh mục đầu số (YC 2.0) + retry / khung giờ không gửi (YC 4.0, 9.0). Ghi: SUPER_ADMIN. */
@RestController
@RequestMapping(ApiPaths.V1 + "/settings")
@RequiredArgsConstructor
@Tag(name = "settings", description = "Cấu hình gửi tin")
public class SettingsController {

    public record SenderNumberDto(Long id, String code, SenderKind kind, String description, boolean enabled) {
        static SenderNumberDto from(SenderNumber s) {
            return new SenderNumberDto(s.getId(), s.getCode(), s.getKind(), s.getDescription(), Boolean.TRUE.equals(s.getEnabled()));
        }
    }

    public record SenderNumberRequest(@NotBlank @Size(max = 32) String code, @NotNull SenderKind kind,
                                      @Size(max = 255) String description, boolean enabled) {
    }

    private final SettingsService service;

    @GetMapping("/sender-numbers")
    @Operation(summary = "Danh mục đầu số gửi SMS")
    public ApiResponse<List<SenderNumberDto>> senders() {
        return ApiResponse.ok(service.listSenders().stream().map(SenderNumberDto::from).toList());
    }

    @PostMapping("/sender-numbers")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Thêm đầu số", description = "Trùng mã → 409 SENDER_CODE_DUPLICATE.")
    @Auditable(actionType = "CREATE", entityType = "SENDER_NUMBER", entityId = "#result.id",
            summary = "'Thêm đầu số ' + #result.code")
    public ApiResponse<SenderNumberDto> createSender(@Valid @RequestBody SenderNumberRequest req) {
        return ApiResponse.ok(SenderNumberDto.from(service.createSender(req.code(), req.kind(), req.description(),
                req.enabled(), CurrentUser.require().username())));
    }

    @PutMapping("/sender-numbers/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Sửa / bật / tắt đầu số")
    @Auditable(actionType = "UPDATE", entityType = "SENDER_NUMBER", entityId = "#id",
            summary = "'Cập nhật đầu số ' + #result.code")
    public ApiResponse<SenderNumberDto> updateSender(@PathVariable Long id, @Valid @RequestBody SenderNumberRequest req) {
        return ApiResponse.ok(SenderNumberDto.from(service.updateSender(id, req.code(), req.kind(), req.description(),
                req.enabled(), CurrentUser.require().username())));
    }

    @GetMapping("/notification")
    @Operation(summary = "Cấu hình gửi tin (retry, hoàn tài nguyên, khung giờ không gửi, ngôn ngữ mặc định)")
    public ApiResponse<NotificationSettings> notification() {
        return ApiResponse.ok(service.notification());
    }

    @PutMapping("/notification")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Cập nhật cấu hình gửi tin")
    @Auditable(actionType = "UPDATE", entityType = "SETTING", entityId = "'notification'",
            summary = "'Cập nhật cấu hình gửi tin'")
    public ApiResponse<NotificationSettings> updateNotification(@RequestBody NotificationSettings req) {
        return ApiResponse.ok(service.updateNotification(req, CurrentUser.require().username()));
    }
}
