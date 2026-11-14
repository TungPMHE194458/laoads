package com.unitel.laoads.domain.workspace;

/** Vai trò thành viên trong workspace (contract §2). Tên i18n: key {@code role.<CODE>}. */
public enum MemberRole {
    OWNER("Chủ sở hữu"),
    ADMIN("Quản trị viên"),
    MEMBER("Thành viên");

    private final String defaultName;

    MemberRole(String defaultName) {
        this.defaultName = defaultName;
    }

    public String defaultName() {
        return defaultName;
    }

    public String messageKey() {
        return "role." + name();
    }
}
