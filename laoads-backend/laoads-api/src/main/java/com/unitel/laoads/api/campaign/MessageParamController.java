package com.unitel.laoads.api.campaign;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.domain.campaign.EventTemplates;
import com.unitel.laoads.domain.campaign.MessageParams;
import com.unitel.laoads.domain.engine.WhatsAppTemplateSource;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Danh mục dùng khi soạn chiến dịch — dùng chung mọi workspace, chỉ cần đăng nhập: tham số cá nhân hoá nội dung tin,
 * template sự kiện (SRS 3.2.1.6).
 */
@RestController
@RequestMapping(ApiPaths.V1)
@Tag(name = "campaign", description = "Chiến dịch")
@RequiredArgsConstructor
public class MessageParamController {

    private final WhatsAppTemplateSource whatsappTemplates;

    /**
     * {@code source}: TARGET (lấy từ thuê bao nhận tin) | SUBSCRIBER (dữ liệu BI trong LADS_SUBSCRIBER, có thể trống);
     * {@code maxLength}: độ dài tối đa của giá trị, để ước tính độ dài tin sau khi thay.
     */
    public record MessageParamDto(String key, String token, String source, int maxLength, String example) {
    }

    @GetMapping("/message-params")
    @Operation(summary = "Danh mục tham số nội dung tin",
            description = "Viết trong nội dung / tiêu đề dạng {{key}} hoặc {{key|giá trị mặc định}}. Engine thay theo "
                    + "từng thuê bao lúc gửi; thiếu dữ liệu → giá trị mặc định hoặc rỗng. Gửi duyệt chiến dịch có tham số "
                    + "ngoài danh mục → 400 VALIDATION_ERROR (error.validation.unknownParam).")
    public ApiResponse<List<MessageParamDto>> messageParams() {
        return ApiResponse.ok(MessageParams.PARAMS.stream()
                .map(p -> new MessageParamDto(p.key(), "{{" + p.key() + "}}", p.source().name(), p.maxLength(), p.example()))
                .toList());
    }

    public record EventTemplateDto(String code) {
    }

    @GetMapping("/event-templates")
    @Operation(summary = "Danh mục template sự kiện",
            description = "Chiến dịch chạy theo sự kiện chọn một template; hệ thống ngoài gọi POST /v1/events với "
                    + "eventType = mã template. Nhãn hiển thị do FE dịch (campaign.event.<code>).")
    public ApiResponse<List<EventTemplateDto>> eventTemplates() {
        return ApiResponse.ok(EventTemplates.CODES.stream().map(EventTemplateDto::new).toList());
    }

    @GetMapping("/whatsapp/templates")
    @Operation(summary = "Danh mục template WhatsApp đã duyệt (từ nhà cung cấp)",
            description = "Hành động WhatsApp chế độ TEMPLATE chọn một template ở đây (như laoads.com). query: lọc theo tên; "
                    + "mặc định chỉ trả template APPROVED, tối đa 200 dòng. Bản hiện tại là danh mục giả lập.")
    public ApiResponse<List<WhatsAppTemplateSource.Template>> whatsappTemplates(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean includeUnapproved) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        return ApiResponse.ok(whatsappTemplates.templates().stream()
                .filter(t -> includeUnapproved || t.approved())
                .filter(t -> q.isEmpty() || t.name().toLowerCase(java.util.Locale.ROOT).contains(q))
                .limit(200).toList());
    }
}
