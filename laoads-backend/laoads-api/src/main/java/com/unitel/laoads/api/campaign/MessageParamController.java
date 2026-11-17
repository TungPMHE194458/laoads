package com.unitel.laoads.api.campaign;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.domain.campaign.MessageParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Danh mục tham số cá nhân hoá dùng trong nội dung tin — dùng chung mọi workspace, chỉ cần đăng nhập. */
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
}
