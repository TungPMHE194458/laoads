package com.unitel.laoads.domain.workspace;

import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;

import java.util.Objects;

/**
 * Quy tắc thành viên (SRS 3.4 / contract §2) — thuần logic, không DB, để test độc lập.
 * {@code actorRole} = vai trò hiệu lực của người thao tác trong workspace
 * (SUPER_ADMIN được coi như OWNER; null nếu không phải thành viên).
 */
public final class MemberRules {

    private MemberRules() {
    }

    /** Chỉ OWNER được gán vai trò OWNER. */
    public static void checkAssign(MemberRole actorRole, MemberRole newRole) {
        if (newRole == MemberRole.OWNER && actorRole != MemberRole.OWNER) {
            throw ownerOnly();
        }
    }

    /**
     * Đổi vai trò: không tự đổi vai trò của mình; gán hoặc thu hồi OWNER (vai trò cũ hoặc mới là OWNER)
     * chỉ OWNER được làm.
     */
    public static void checkChangeRole(Long actorUserId, MemberRole actorRole,
                                       Long targetUserId, MemberRole currentRole, MemberRole newRole) {
        if (Objects.equals(actorUserId, targetUserId)) {
            throw new LaoadsException(ErrorCode.CANNOT_CHANGE_SELF,
                    "Không thể tự thay đổi vai trò của chính mình");
        }
        if ((currentRole == MemberRole.OWNER || newRole == MemberRole.OWNER) && actorRole != MemberRole.OWNER) {
            throw ownerOnly();
        }
    }

    /** Gỡ thành viên: không gỡ OWNER; không tự gỡ mình. */
    public static void checkRemove(Long actorUserId, Long targetUserId, MemberRole targetRole) {
        if (targetRole == MemberRole.OWNER) {
            throw new LaoadsException(ErrorCode.CANNOT_REMOVE_OWNER,
                    "Không thể gỡ thành viên có vai trò OWNER");
        }
        if (Objects.equals(actorUserId, targetUserId)) {
            throw new LaoadsException(ErrorCode.CANNOT_REMOVE_SELF,
                    "Không thể tự gỡ chính mình khỏi workspace");
        }
    }

    private static LaoadsException ownerOnly() {
        return new LaoadsException(ErrorCode.OWNER_ONLY,
                "Chỉ OWNER mới được gán hoặc thu hồi vai trò OWNER");
    }
}
