package com.unitel.laoads.api.pricing;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.api.common.PageParams;
import com.unitel.laoads.api.pricing.dto.PriceItemInput;
import com.unitel.laoads.api.pricing.dto.PriceQuoteDto;
import com.unitel.laoads.api.pricing.dto.PriceQuoteRequest;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.pricing.PriceQuoteService;
import com.unitel.laoads.domain.pricing.PriceQuoteStatus;
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

/** Contract §7 — bảng giá. Đọc: mọi user đăng nhập; ghi: SUPER_ADMIN. */
@RestController
@RequestMapping(ApiPaths.V1 + "/price-quotes")
@RequiredArgsConstructor
@Tag(name = "price-quote", description = "Bảng giá (Quản lý chi phí)")
public class PriceQuoteController {

    private final PriceQuoteService service;

    @GetMapping
    @Operation(summary = "Danh sách bảng giá (createdAt giảm dần)")
    public ApiResponse<List<PriceQuoteDto>> list(@RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size,
                                                 @RequestParam(required = false) PriceQuoteStatus status) {
        return ApiResponse.page(service.list(status, PageParams.of(page, size)), PriceQuoteDto::from);
    }

    @GetMapping("/active")
    @Operation(summary = "Bảng giá LOCKED đang áp dụng, hoặc data=null")
    public ApiResponse<PriceQuoteDto> active() {
        return ApiResponse.ok(service.active().map(PriceQuoteDto::from).orElse(null));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết bảng giá")
    public ApiResponse<PriceQuoteDto> get(@PathVariable Long id) {
        return ApiResponse.ok(PriceQuoteDto.from(service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Tạo bảng giá DRAFT")
    @Auditable(actionType = "CREATE", entityType = "PRICE_QUOTE",
            summary = "'Tạo bảng giá ' + #result.name")
    public ApiResponse<PriceQuoteDto> create(@Valid @RequestBody PriceQuoteRequest req) {
        return ApiResponse.ok(PriceQuoteDto.from(service.create(req.name(), req.note(),
                req.items().stream().map(PriceItemInput::toCommand).toList(), CurrentUser.require().username())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Sửa bảng giá — chỉ DRAFT", description = "Không phải DRAFT → 409 PRICE_QUOTE_NOT_DRAFT.")
    @Auditable(actionType = "UPDATE", entityType = "PRICE_QUOTE", entityId = "#id",
            summary = "'Cập nhật bảng giá ' + #result.name")
    public ApiResponse<PriceQuoteDto> update(@PathVariable Long id, @Valid @RequestBody PriceQuoteRequest req) {
        return ApiResponse.ok(PriceQuoteDto.from(service.update(id, req.name(), req.note(),
                req.items().stream().map(PriceItemInput::toCommand).toList(), CurrentUser.require().username())));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Khoá bảng giá DRAFT → LOCKED (bảng LOCKED cũ → ENDED)",
            description = "Không phải DRAFT → 409 PRICE_QUOTE_NOT_DRAFT.")
    @Auditable(actionType = "LOCK", entityType = "PRICE_QUOTE", entityId = "#id",
            summary = "'Khoá (áp dụng) bảng giá ' + #result.name")
    public ApiResponse<PriceQuoteDto> lock(@PathVariable Long id) {
        return ApiResponse.ok(PriceQuoteDto.from(service.lock(id, CurrentUser.require().username())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Xoá bảng giá — chỉ DRAFT", description = "Không phải DRAFT → 409 PRICE_QUOTE_NOT_DRAFT.")
    @Auditable(actionType = "DELETE", entityType = "PRICE_QUOTE", entityId = "#id",
            summary = "'Xoá bảng giá id=' + #id")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id, CurrentUser.require().username());
        return ApiResponse.ok();
    }
}
