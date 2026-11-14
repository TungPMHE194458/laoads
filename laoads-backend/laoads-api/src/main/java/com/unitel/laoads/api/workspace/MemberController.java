package com.unitel.laoads.api.workspace;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.api.common.PageParams;
import com.unitel.laoads.api.common.WorkspaceScoped;
import com.unitel.laoads.api.workspace.dto.AddMemberRequest;
import com.unitel.laoads.api.workspace.dto.ChangeRoleRequest;
import com.unitel.laoads.api.workspace.dto.MemberDto;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.workspace.MemberRole;
import com.unitel.laoads.domain.workspace.MemberService;
import com.unitel.laoads.domain.workspace.MemberStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contract §5 — thành viên 🏢. {@code {id}} trong path phải trùng header X-Workspace-Id
 * (lệch → 400 INVALID_PARAM; thiếu header → 400 WORKSPACE_REQUIRED).
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/workspaces/{id}/members")
@RequiredArgsConstructor
@WorkspaceScoped
@Tag(name = "member", description = "Thành viên workspace")
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    @PreAuthorize("@perm.hasForPath(#id, 'WORKSPACE_VIEW')")
    @Operation(summary = "Danh sách thành viên", description = "query tìm theo tên/username/email; "
            + "không truyền status → trả cả ACTIVE và REVOKED.")
    public ApiResponse<List<MemberDto>> list(@PathVariable Long id,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size,
                                             @RequestParam(required = false) String query,
                                             @RequestParam(required = false) MemberRole role,
                                             @RequestParam(required = false) MemberStatus status) {
        return ApiResponse.page(memberService.list(id, query, role, status, PageParams.of(page, size)),
                MemberDto::from);
    }

    @PostMapping
    @PreAuthorize("@perm.hasForPath(#id, 'MEMBER_MANAGE')")
    @Operation(summary = "Thêm thành viên", description = "404 USER_NOT_FOUND, 409 MEMBER_DUPLICATE, "
            + "403 OWNER_ONLY (gán OWNER khi không phải OWNER).")
    @Auditable(actionType = "CREATE", entityType = "MEMBER", workspaceId = "#id",
            summary = "'Thêm thành viên ' + #result.username + ' với vai trò ' + #result.role")
    public ApiResponse<MemberDto> add(@PathVariable Long id, @Valid @RequestBody AddMemberRequest req) {
        return ApiResponse.ok(MemberDto.from(memberService.add(CurrentUser.require(), id, req.identifier(),
                req.role())));
    }

    @PutMapping("/{memberId}/role")
    @PreAuthorize("@perm.hasForPath(#id, 'MEMBER_MANAGE')")
    @Operation(summary = "Đổi vai trò thành viên", description = "400 CANNOT_CHANGE_SELF, 403 OWNER_ONLY.")
    @Auditable(actionType = "CHANGE_ROLE", entityType = "MEMBER", entityId = "#memberId", workspaceId = "#id",
            summary = "'Đổi vai trò ' + #result.username + ' thành ' + #result.role")
    public ApiResponse<MemberDto> changeRole(@PathVariable Long id, @PathVariable Long memberId,
                                             @Valid @RequestBody ChangeRoleRequest req) {
        return ApiResponse.ok(MemberDto.from(memberService.changeRole(CurrentUser.require(), id, memberId,
                req.role())));
    }

    @DeleteMapping("/{memberId}")
    @PreAuthorize("@perm.hasForPath(#id, 'MEMBER_MANAGE')")
    @Operation(summary = "Gỡ thành viên (status → REVOKED)",
            description = "400 CANNOT_REMOVE_OWNER / CANNOT_REMOVE_SELF.")
    @Auditable(actionType = "DELETE", entityType = "MEMBER", entityId = "#memberId", workspaceId = "#id",
            summary = "'Gỡ thành viên (memberId=' + #memberId + ')'")
    public ApiResponse<Void> remove(@PathVariable Long id, @PathVariable Long memberId) {
        memberService.remove(CurrentUser.require(), id, memberId);
        return ApiResponse.ok();
    }
}
