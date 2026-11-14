package com.unitel.laoads.domain.permission;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Danh mục permission (contract §2). Danh mục cố định trong code; ma trận role → permission
 * lưu ở LADS_ROLE_PERMISSION (SUPER_ADMIN sửa được). Mô tả i18n: key {@code permission.<CODE>}.
 * Thứ tự enum = thứ tự hiển thị.
 */
public enum Permission {
    WORKSPACE_VIEW(PermissionGroup.WORKSPACE, "Xem thông tin workspace"),
    WORKSPACE_UPDATE(PermissionGroup.WORKSPACE, "Cập nhật thông tin workspace"),
    MEMBER_MANAGE(PermissionGroup.MEMBER, "Quản lý thành viên"),
    CAMPAIGN_VIEW(PermissionGroup.CAMPAIGN, "Xem chiến dịch"),
    CAMPAIGN_CREATE(PermissionGroup.CAMPAIGN, "Tạo chiến dịch"),
    CAMPAIGN_UPDATE(PermissionGroup.CAMPAIGN, "Sửa chiến dịch"),
    CAMPAIGN_SUBMIT(PermissionGroup.CAMPAIGN, "Gửi duyệt chiến dịch"),
    CAMPAIGN_APPROVE(PermissionGroup.CAMPAIGN, "Phê duyệt chiến dịch"),
    CAMPAIGN_DELETE(PermissionGroup.CAMPAIGN, "Xoá chiến dịch"),
    SEGMENT_MANAGE(PermissionGroup.SEGMENT, "Quản lý tập khách hàng (segment)"),
    SECRET_KEY_MANAGE(PermissionGroup.SECRET_KEY, "Quản lý secret key"),
    REPORT_VIEW(PermissionGroup.REPORT, "Xem báo cáo");

    private final PermissionGroup group;
    private final String defaultDescription;

    Permission(PermissionGroup group, String defaultDescription) {
        this.group = group;
        this.defaultDescription = defaultDescription;
    }

    public PermissionGroup group() {
        return group;
    }

    public String defaultDescription() {
        return defaultDescription;
    }

    public String messageKey() {
        return "permission." + name();
    }

    public static Optional<Permission> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(p -> p.name().equals(code)).findFirst();
    }

    public static List<String> allCodes() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
