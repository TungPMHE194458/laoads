package com.unitel.laoads.api.campaign;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.domain.engine.EventService;
import com.unitel.laoads.domain.engine.TrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Endpoint KHÔNG cần đăng nhập (permitAll trong SecurityConfig):
 * link tracking cho người nhận tin, và API sự kiện cho hệ thống ngoài (xác thực bằng API key workspace).
 */
@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
@Tag(name = "public", description = "Link tracking & API sự kiện")
public class PublicEngineController {

    public record EventRequest(String eventType, String isdn) {
    }

    private final TrackingService tracking;
    private final EventService events;

    @GetMapping("/t/{token}")
    @Operation(summary = "Link tracking: ghi nhận click rồi chuyển hướng tới URL gốc (302)")
    public ResponseEntity<Void> track(@PathVariable String token) {
        return tracking.click(token)
                .map(url -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/events")
    @Operation(summary = "Báo sự kiện thuê bao (header X-Api-Key) → ghi danh vào chiến dịch EVENT đang chạy cùng template",
            description = "Key sai / bị thu hồi → 401 API_KEY_INVALID.")
    public ApiResponse<EventService.Accepted> event(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                                    @RequestBody EventRequest req) {
        return ApiResponse.ok(events.ingest(apiKey, req.eventType(), req.isdn()));
    }
}
