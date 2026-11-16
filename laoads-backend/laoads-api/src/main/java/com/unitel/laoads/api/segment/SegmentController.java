package com.unitel.laoads.api.segment;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.api.common.PageParams;
import com.unitel.laoads.api.common.WorkspaceContext;
import com.unitel.laoads.api.common.WorkspaceScoped;
import com.unitel.laoads.domain.audience.Audience;
import com.unitel.laoads.domain.audience.AudienceRequests;
import com.unitel.laoads.domain.audience.AudienceService;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.Isdns;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.Times;
import com.unitel.laoads.domain.segment.IsdnFileTemplate;
import com.unitel.laoads.domain.segment.Segment;
import com.unitel.laoads.domain.segment.SegmentAttributeCatalog;
import com.unitel.laoads.domain.segment.SegmentCondition;
import com.unitel.laoads.domain.segment.SegmentEnums;
import com.unitel.laoads.domain.segment.SegmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

/** Tệp khách hàng (SRS 3.2.2, 3.5) — theo workspace (X-Workspace-Id). */
@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
@WorkspaceScoped
@Tag(name = "segment", description = "Tệp khách hàng")
public class SegmentController {

    public record SegmentDto(Long id, String name, String description, SegmentEnums.Type type, SegmentEnums.Source source,
                             SegmentEnums.Status status, int totalUsers, String fileName, List<SegmentCondition> conditions,
                             int referencedBy, String errorMessage, OffsetDateTime createdAt, String createdBy,
                             OffsetDateTime updatedAt, Integer invalidRows, Audience.Request audience, long importedRows) {
    }

    // ───── Audience API (OCS / BCCS3) — đầu vào dạng chuỗi để báo lỗi theo từng trường ─────

    public record AudienceColumnDto(String columnName, String dataType, String targetField) {
    }

    public record AudienceConditionDto(String columnName, String operator, String dataType, String logicalOperator,
                                       String value) {
    }

    public record AudienceRequestDto(String tableName, List<AudienceColumnDto> selectedColumns,
                                     List<AudienceConditionDto> conditions) {

        AudienceRequests.RawRequest raw() {
            return new AudienceRequests.RawRequest(tableName,
                    selectedColumns == null ? null : selectedColumns.stream().map(c -> c == null ? null
                            : new AudienceRequests.RawColumn(c.columnName(), c.dataType(), c.targetField())).toList(),
                    conditions == null ? null : conditions.stream().map(c -> c == null ? null
                            : new AudienceRequests.RawCondition(c.columnName(), c.operator(), c.dataType(),
                            c.logicalOperator(), c.value())).toList());
        }
    }

    public record CreateAudienceRequest(@NotBlank @Size(max = 255) String name, @Size(max = 1000) String description,
                                        @NotNull String source, @NotNull AudienceRequestDto request) {
    }

    public record AttributeDto(String field, String group, String dataType, List<String> options) {
    }

    public record MemberDto(String recipientKey, String phone, Map<String, Object> attributes) {
    }

    public record CreateQueryRequest(@NotBlank @Size(max = 255) String name, @Size(max = 1000) String description,
                                     String type, List<SegmentCondition> conditions) {
    }

    public record EstimateRequest(List<SegmentCondition> conditions) {
    }

    public record BulkDeleteRequest(@NotEmpty @Size(max = 200) List<Long> ids) {
    }

    static final String TEMPLATE_FILE = "isdn_template.xlsx";
    private static final int TEMPLATE_LINES = 5; // segment.template.line1..5 trong messages*.properties

    private final SegmentService service;
    private final AudienceService audienceService;
    private final WorkspaceContext workspaceContext;
    private final MessageSource messages;

    @GetMapping("/segment-attributes")
    @Operation(summary = "Danh mục thuộc tính thuê bao dùng để lọc")
    public ApiResponse<List<AttributeDto>> attributes() {
        return ApiResponse.ok(SegmentAttributeCatalog.ATTRIBUTES.stream()
                .map(a -> new AttributeDto(a.field(), a.group(), a.dataType().name(), a.options())).toList());
    }

    @GetMapping("/segments")
    @PreAuthorize("@perm.has('CAMPAIGN_VIEW')")
    @Operation(summary = "Danh sách tệp khách hàng")
    public ApiResponse<List<SegmentDto>> list(@RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              @RequestParam(required = false) String query,
                                              @RequestParam(required = false) SegmentEnums.Type type,
                                              @RequestParam(required = false) SegmentEnums.Status status) {
        Page<Segment> p = service.list(ws(), query, type, status, PageParams.of(page, size));
        Map<Long, Integer> refs = service.referenceCounts(p.getContent().stream().map(Segment::getId).toList());
        return ApiResponse.page(p, s -> dto(s, refs.getOrDefault(s.getId(), 0), null));
    }

    @GetMapping("/segments/{id}")
    @PreAuthorize("@perm.has('CAMPAIGN_VIEW')")
    @Operation(summary = "Chi tiết tệp khách hàng")
    public ApiResponse<SegmentDto> get(@PathVariable Long id) {
        Segment s = service.get(ws(), id);
        return ApiResponse.ok(dto(s, service.referenceCounts(List.of(id)).getOrDefault(id, 0), null));
    }

    @GetMapping("/segments/{id}/members")
    @PreAuthorize("@perm.has('CAMPAIGN_VIEW')")
    @Operation(summary = "Thành viên tệp (số thuê bao đã che)", description = "Tệp chưa READY → 409 SEGMENT_NOT_READY.")
    public ApiResponse<List<MemberDto>> members(@PathVariable Long id, @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return ApiResponse.page(service.members(ws(), id, PageParams.of(page, size)), m -> {
            Map<String, Object> attrs = new LinkedHashMap<>();
            if (m.province() != null) {
                attrs.put("province", m.province());
            }
            if (m.packageCode() != null) {
                attrs.put("package", m.packageCode());
            }
            if (m.gVolume() != null) {
                attrs.put("g_volume_gb", m.gVolume().setScale(1, java.math.RoundingMode.HALF_UP));
            }
            return new MemberDto(recipientKey(m.isdn()), Isdns.mask(m.isdn()), attrs);
        });
    }

    @PostMapping("/segments/estimate")
    @PreAuthorize("@perm.has('CAMPAIGN_VIEW')")
    @Operation(summary = "Ước tính số thuê bao khớp điều kiện")
    public ApiResponse<Map<String, Long>> estimate(@RequestBody EstimateRequest req) {
        return ApiResponse.ok(Map.of("estimatedCount", service.estimate(req.conditions())));
    }

    @PostMapping(value = "/segments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@perm.has('SEGMENT_MANAGE')")
    @Operation(summary = "Tạo tệp từ thuộc tính (QUERY) — build bất đồng bộ", description = "Trùng tên → 409 SEGMENT_NAME_DUPLICATE.")
    @Auditable(actionType = "CREATE", entityType = "SEGMENT", entityId = "#result.id", workspaceId = "#workspaceHeader",
            summary = "'Tạo tệp KH \"' + #result.name + '\"'")
    public ApiResponse<SegmentDto> create(@Valid @RequestBody CreateQueryRequest req) {
        if (req.type() != null && !"QUERY".equals(req.type())) {
            throw new LaoadsException(ErrorCode.BAD_REQUEST_BODY, "Tệp từ file dùng POST /segments/import (multipart)");
        }
        return ApiResponse.ok(dto(service.createQuery(ws(), req.name(), req.description(), req.conditions(),
                CurrentUser.require().username()), 0, null));
    }

    @PostMapping(value = "/segments/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@perm.has('SEGMENT_MANAGE')")
    @Operation(summary = "Tạo tệp từ file .xlsx / .xls / .csv (cột isdn / msisdn / phone hoặc cột đầu tiên)")
    @Auditable(actionType = "IMPORT", entityType = "SEGMENT", entityId = "#result.id", workspaceId = "#workspaceHeader",
            summary = "'Import file ' + #result.fileName + ' (' + #result.totalUsers + ' số)'")
    public ApiResponse<SegmentDto> importFile(@RequestPart("file") MultipartFile file, @RequestParam String name,
                                              @RequestParam(required = false) String description) {
        try (InputStream in = file.getInputStream()) {
            SegmentService.ImportResult r = service.importFile(ws(), name, description, file.getOriginalFilename(), in,
                    CurrentUser.require().username());
            return ApiResponse.ok(dto(r.segment(), 0, r.invalidRows()));
        } catch (IOException e) {
            throw new LaoadsException(ErrorCode.FILE_INVALID, "File không hợp lệ", "không đọc được file");
        }
    }

    @GetMapping("/segments/import/template")
    @PreAuthorize("@perm.has('SEGMENT_MANAGE')")
    @Operation(summary = "Tải file mẫu import tệp khách hàng (.xlsx)",
            description = "Sheet 1 chỉ có tiêu đề isdn (cột định dạng Text); sheet 2 là hướng dẫn theo Accept-Language.")
    public ResponseEntity<byte[]> importTemplate() {
        Locale locale = LocaleContextHolder.getLocale();
        List<String> lines = IntStream.rangeClosed(1, TEMPLATE_LINES)
                .mapToObj(i -> messages.getMessage("segment.template.line" + i, null, "", locale)).toList();
        byte[] body = IsdnFileTemplate.xlsx(new IsdnFileTemplate.Guide(
                messages.getMessage("segment.template.sheet", null, "Huong dan", locale),
                messages.getMessage("segment.template.title", null, "", locale), lines));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(TEMPLATE_FILE, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    // ───────────── Audience API (OCS / BCCS3) ─────────────

    @GetMapping("/segments/audience/sources")
    @PreAuthorize("@perm.has('CAMPAIGN_VIEW')")
    @Operation(summary = "Nguồn Audience API (OCS, BCCS3): đã cấu hình chưa, chạy thật hay giả lập")
    public ApiResponse<List<Audience.SourceInfo>> audienceSources() {
        return ApiResponse.ok(audienceService.sources());
    }

    @GetMapping("/segments/audience/{source}/tables")
    @PreAuthorize("@perm.has('SEGMENT_MANAGE')")
    @Operation(summary = "Danh sách bảng của nguồn", description = "Chưa cấu hình → 400 AUDIENCE_NOT_CONFIGURED; "
            + "mất kết nối / API key sai → 503 AUDIENCE_UNAVAILABLE.")
    public ApiResponse<List<String>> audienceTables(@PathVariable String source) {
        return ApiResponse.ok(audienceService.tables(source(source)));
    }

    @GetMapping("/segments/audience/{source}/tables/{table}/columns")
    @PreAuthorize("@perm.has('SEGMENT_MANAGE')")
    @Operation(summary = "Cột của bảng (kiểu đã chuẩn hoá BOOLEAN / INT / LONG / DOUBLE / DATE / STRING)")
    public ApiResponse<List<Audience.Column>> audienceColumns(@PathVariable String source, @PathVariable String table) {
        return ApiResponse.ok(audienceService.columns(source(source), table));
    }

    @PostMapping("/segments/audience/{source}/verify")
    @PreAuthorize("@perm.has('SEGMENT_MANAGE')")
    @Operation(summary = "Kiểm tra request audience", description = "Sai cục bộ (thiếu cột PHONE, operator không hợp kiểu…) "
            + "→ 400 VALIDATION_ERROR; service đánh giá → 200 {valid, message}.")
    public ApiResponse<Audience.VerifyResult> audienceVerify(@PathVariable String source,
                                                             @RequestBody AudienceRequestDto request) {
        return ApiResponse.ok(audienceService.verify(source(source), request == null ? null : request.raw()));
    }

    @PostMapping("/segments/audience")
    @PreAuthorize("@perm.has('SEGMENT_MANAGE')")
    @Operation(summary = "Tạo tệp từ Audience API (AUDIENCE) — nạp bất đồng bộ bằng stream NDJSON",
            description = "Trùng tên → 409 SEGMENT_NAME_DUPLICATE; service từ chối request → 400 VALIDATION_ERROR (field request).")
    @Auditable(actionType = "CREATE", entityType = "SEGMENT", entityId = "#result.id", workspaceId = "#workspaceHeader",
            summary = "'Tạo tệp KH \"' + #result.name + '\" từ ' + #result.source")
    public ApiResponse<SegmentDto> createAudience(@Valid @RequestBody CreateAudienceRequest req) {
        return ApiResponse.ok(dto(service.createAudience(ws(), req.name(), req.description(), source(req.source()),
                req.request().raw(), CurrentUser.require().username()), 0, null));
    }

    @PostMapping("/segments/{id}/retry")
    @PreAuthorize("@perm.has('SEGMENT_MANAGE')")
    @Operation(summary = "Chạy lại tệp lỗi (AUDIENCE / QUERY)",
            description = "OCS nạp tiếp từ offset đã xử lý, BCCS3 nạp lại từ đầu. Không phải tệp FAILED → 409 INVALID_TRANSITION.")
    @Auditable(actionType = "UPDATE", entityType = "SEGMENT", entityId = "#id", workspaceId = "#workspaceHeader",
            summary = "'Chạy lại tệp KH \"' + #result.name + '\"'")
    public ApiResponse<SegmentDto> retry(@PathVariable Long id) {
        return ApiResponse.ok(dto(service.retry(ws(), id, CurrentUser.require().username()), 0, null));
    }

    @PostMapping("/segments/bulk-delete")
    @PreAuthorize("@perm.has('SEGMENT_MANAGE')")
    @Operation(summary = "Xoá nhiều tệp (bỏ qua tệp đang được chiến dịch dùng)")
    @Auditable(actionType = "DELETE", entityType = "SEGMENT", workspaceId = "#workspaceHeader",
            summary = "'Xoá ' + #result.deleted + ' tệp khách hàng'")
    public ApiResponse<SegmentService.BulkDeleteResult> bulkDelete(@Valid @RequestBody BulkDeleteRequest req) {
        return ApiResponse.ok(service.bulkDelete(ws(), req.ids()));
    }

    private SegmentDto dto(Segment s, int refs, Integer invalidRows) {
        return new SegmentDto(s.getId(), s.getName(), s.getDescription(), s.getType(), s.getSource(), s.getStatus(),
                s.getTotalUsers(), s.getFileName(), service.conditionsOf(s), refs, s.getErrorMessage(),
                Times.toOffset(s.getCreatedAt()), s.getCreatedBy(), Times.toOffset(s.getUpdatedAt()), invalidRows,
                s.getType() == SegmentEnums.Type.AUDIENCE ? service.audienceOf(s) : null,
                s.getImportedRows() == null ? 0 : s.getImportedRows());
    }

    private static Audience.Source source(String raw) {
        try {
            return Audience.Source.valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw LaoadsException.validation(List.of(new FieldViolation("source", "error.validation.invalidValue",
                    "Giá trị không hợp lệ")));
        }
    }

    private long ws() {
        return workspaceContext.requireWorkspaceId();
    }

    /** Khoá ẩn danh của thuê bao (không lộ số thật). */
    static String recipientKey(String isdn) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(isdn.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
