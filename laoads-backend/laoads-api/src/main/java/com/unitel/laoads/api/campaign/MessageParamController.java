package com.unitel.laoads.api.campaign;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.domain.campaign.EventTemplates;
import com.unitel.laoads.domain.campaign.MessageParams;
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
public class MessageParamController {

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
}
