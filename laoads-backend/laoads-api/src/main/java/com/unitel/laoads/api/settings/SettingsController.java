package com.unitel.laoads.api.settings;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.settings.NotificationSettings;
import com.unitel.laoads.domain.settings.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chính sách gửi tin chung: retry / khung giờ không gửi (YC 4.0, 9.0). Ghi: SUPER_ADMIN. Đầu số: {@link SenderNumberController}. */
@RestController
@RequestMapping(ApiPaths.V1 + "/settings")
@RequiredArgsConstructor
@Tag(name = "settings", description = "Cấu hình gửi tin")
public class SettingsController {

    private final SettingsService service;

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
