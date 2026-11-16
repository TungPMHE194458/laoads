package com.unitel.laoads.api.settings;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.api.common.WorkspaceContext;
import com.unitel.laoads.api.common.WorkspaceScoped;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.campaign.CampaignEnums.Channel;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.Times;
import com.unitel.laoads.domain.settings.ConnectionTestCode;
import com.unitel.laoads.domain.settings.SenderKind;
import com.unitel.laoads.domain.settings.SenderNumber;
import com.unitel.laoads.domain.settings.SenderNumberService;
import com.unitel.laoads.domain.settings.SenderNumberService.GatewayInput;
import com.unitel.laoads.domain.settings.SenderNumberService.PolicyInput;
import com.unitel.laoads.domain.settings.SenderNumberService.SenderInput;
import com.unitel.laoads.domain.settings.SenderNumberService.TestOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Đầu số gửi tin + cấu hình riêng (kênh, kết nối gateway, phạm vi workspace, chính sách gửi) — YC 2.0.
 * Quản trị: SUPER_ADMIN. Wizard chiến dịch đọc {@code /available} theo workspace.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/settings/sender-numbers")
@RequiredArgsConstructor
@Tag(name = "sender-number", description = "Đầu số gửi tin và cấu hình từng đầu số")
public class SenderNumberController {

    // ───── DTO ─────

    public record TestResultDto(boolean ok, ConnectionTestCode code, String detail, Long latencyMs,
                                OffsetDateTime testedAt) {
    }

    /** protocol: NONE | SMPP | HTTP. Mật khẩu không bao giờ trả về — chỉ {@code hasPassword}. */
    public record GatewayDto(String protocol, String host, Integer port, String systemType, String url, String username,
                             boolean hasPassword, Integer tps, TestResultDto lastTest) {
    }

    /** Mọi trường null = theo chính sách chung. */
    public record PolicyDto(Integer retryMaxAttempts, Integer retryIntervalMinutes, Boolean silentHoursEnabled,
                            String silentFrom, String silentTo, Long dailyLimit) {
    }

    public record SenderDto(Long id, String code, SenderKind kind, String description, boolean enabled,
                            List<String> channels, boolean allWorkspaces, List<Long> workspaceIds, GatewayDto gateway,
                            PolicyDto policy, long sentToday, OffsetDateTime createdAt, String createdBy,
                            OffsetDateTime updatedAt, String updatedBy) {
    }

    public record AvailableSenderDto(Long id, String code, SenderKind kind, String description, List<String> channels) {
    }

    /** password: null/rỗng = giữ mật khẩu đã lưu; khác rỗng = thay; clearPassword = xoá. */
    public record GatewayRequest(String protocol, String host, Integer port, String systemType, String url,
                                 String username, String password, Boolean clearPassword, Integer tps) {
    }

    public record SenderRequest(@NotBlank @Size(max = 32) String code, @NotNull SenderKind kind,
                                @Size(max = 255) String description, boolean enabled, List<String> channels,
                                boolean allWorkspaces, List<Long> workspaceIds, GatewayRequest gateway,
                                PolicyDto policy) {

        SenderInput toInput() {
            GatewayInput gw = gateway == null ? GatewayInput.NONE
                    : new GatewayInput(gateway.protocol(), gateway.host(), gateway.port(), gateway.systemType(),
                    gateway.url(), gateway.username(), gateway.password(), Boolean.TRUE.equals(gateway.clearPassword()),
                    gateway.tps());
            PolicyInput p = policy == null ? PolicyInput.INHERIT
                    : new PolicyInput(policy.retryMaxAttempts(), policy.retryIntervalMinutes(),
                    policy.silentHoursEnabled(), policy.silentFrom(), policy.silentTo(), policy.dailyLimit());
            return new SenderInput(code, kind, description, enabled, channels, allWorkspaces, workspaceIds, gw, p);
        }
    }

    public record EnabledRequest(@NotNull Boolean enabled) {
    }

    private final SenderNumberService service;
    private final WorkspaceContext workspaceContext;

    // ───── Quản trị (SUPER_ADMIN) ─────

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Danh sách đầu số kèm cấu hình", description = "Kèm số tin đã gửi hôm nay (giờ Lào).")
    public ApiResponse<List<SenderDto>> list() {
        Map<String, Long> today = service.sentToday();
        return ApiResponse.ok(service.list().stream().map(s -> dto(s, today)).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Chi tiết đầu số")
    public ApiResponse<SenderDto> get(@PathVariable Long id) {
        return ApiResponse.ok(dto(service.get(id), service.sentToday()));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Thêm đầu số", description = "Trùng mã → 409 SENDER_CODE_DUPLICATE; dữ liệu sai → 400 VALIDATION_ERROR.")
    @Auditable(actionType = "CREATE", entityType = "SENDER_NUMBER", entityId = "#result.id",
            summary = "'Thêm đầu số ' + #result.code")
    public ApiResponse<SenderDto> create(@Valid @RequestBody SenderRequest req) {
        return ApiResponse.ok(dto(service.create(req.toInput(), user()), service.sentToday()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Sửa đầu số và cấu hình", description = "gateway.password bỏ trống = giữ mật khẩu đã lưu.")
    @Auditable(actionType = "UPDATE", entityType = "SENDER_NUMBER", entityId = "#id",
            summary = "'Cập nhật đầu số ' + #result.code")
    public ApiResponse<SenderDto> update(@PathVariable Long id, @Valid @RequestBody SenderRequest req) {
        return ApiResponse.ok(dto(service.update(id, req.toInput(), user()), service.sentToday()));
    }

    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Bật / tắt đầu số")
    @Auditable(actionType = "UPDATE", entityType = "SENDER_NUMBER", entityId = "#id",
            summary = "(#result.enabled ? 'Bật' : 'Tắt') + ' đầu số ' + #result.code")
    public ApiResponse<SenderDto> setEnabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest req) {
        return ApiResponse.ok(dto(service.setEnabled(id, req.enabled(), user()), service.sentToday()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Xoá đầu số",
            description = "Còn chiến dịch chưa kết thúc / huỷ (kể cả Nháp, Bị từ chối) dùng đầu số → 409 SENDER_IN_USE; "
                    + "khi đó tắt đầu số thay vì xoá.")
    @Auditable(actionType = "DELETE", entityType = "SENDER_NUMBER", entityId = "#id",
            summary = "'Xoá đầu số ' + #result.code")
    public ApiResponse<SenderDto> delete(@PathVariable Long id) {
        return ApiResponse.ok(dto(service.delete(id, user()), Map.of()));
    }

    @PostMapping("/{id}/test-connection")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Kiểm tra kết nối gateway theo cấu hình đã lưu",
            description = "SMPP: bind_transceiver rồi unbind; HTTP: GET URL. Chưa cấu hình → 400 VALIDATION_ERROR.")
    @Auditable(actionType = "TEST", entityType = "SENDER_NUMBER", entityId = "#id",
            summary = "'Kiểm tra kết nối đầu số id=' + #id + ': ' + #result.code")
    public ApiResponse<TestResultDto> testConnection(@PathVariable Long id) {
        TestOutcome r = service.testConnection(id, user());
        return ApiResponse.ok(new TestResultDto(r.ok(), r.code(), r.detail(), r.latencyMs(), Times.toOffset(r.testedAt())));
    }

    // ───── Cho wizard chiến dịch (theo workspace) ─────

    @GetMapping("/available")
    @WorkspaceScoped
    @PreAuthorize("@perm.has('CAMPAIGN_VIEW')")
    @Operation(summary = "Đầu số workspace hiện tại được dùng",
            description = "Chỉ đầu số đang bật, trong phạm vi workspace và hỗ trợ kênh (bỏ trống = mọi kênh).")
    public ApiResponse<List<AvailableSenderDto>> available(@RequestParam(required = false) String channel) {
        Channel c = null;
        if (channel != null && !channel.isBlank()) {
            try {
                c = Channel.valueOf(channel.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw LaoadsException.validation(List.of(new FieldViolation("channel", "error.validation.invalidValue",
                        "Giá trị không hợp lệ")));
            }
        }
        return ApiResponse.ok(service.available(workspaceContext.requireWorkspaceId(), c).stream()
                .map(s -> new AvailableSenderDto(s.getId(), s.getCode(), s.getKind(), s.getDescription(), channels(s)))
                .toList());
    }

    // ───── map ─────

    static SenderDto dto(SenderNumber s, Map<String, Long> sentToday) {
        TestResultDto lastTest = s.getGwTestAt() == null ? null
                : new TestResultDto(Boolean.TRUE.equals(s.getGwTestOk()), s.getGwTestCode(), s.getGwTestDetail(),
                s.getGwTestLatencyMs(), Times.toOffset(s.getGwTestAt()));
        GatewayDto gw = new GatewayDto(s.getGwProtocol() == null ? "NONE" : s.getGwProtocol().name(), s.getGwHost(),
                s.getGwPort(), s.getGwSystemType(), s.getGwUrl(), s.getGwUsername(), s.getGwPasswordEnc() != null,
                s.getGwTps(), lastTest);
        PolicyDto policy = new PolicyDto(s.getRetryMax(), s.getRetryIntervalMin(), s.getSilentEnabled(),
                s.getSilentFrom(), s.getSilentTo(), s.getDailyLimit());
        List<Long> ws = s.isAllWorkspaces() || s.getWorkspaceIds() == null ? List.of()
                : s.getWorkspaceIds().stream().sorted().toList();
        return new SenderDto(s.getId(), s.getCode(), s.getKind(), s.getDescription(), s.isEnabled(), channels(s),
                s.isAllWorkspaces(), ws, gw, policy, sentToday.getOrDefault(s.getCode(), 0L),
                Times.toOffset(s.getCreatedAt()), s.getCreatedBy(), Times.toOffset(s.getUpdatedAt()), s.getUpdatedBy());
    }

    private static List<String> channels(SenderNumber s) {
        return s.channelSet().stream().map(Channel::name).toList();
    }

    private static String user() {
        return CurrentUser.require().username();
    }
}
