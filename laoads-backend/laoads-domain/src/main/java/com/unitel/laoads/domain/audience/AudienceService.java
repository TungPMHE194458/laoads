package com.unitel.laoads.domain.audience;

import com.unitel.laoads.domain.audience.Audience.Column;
import com.unitel.laoads.domain.audience.Audience.DataType;
import com.unitel.laoads.domain.audience.Audience.Request;
import com.unitel.laoads.domain.audience.Audience.Source;
import com.unitel.laoads.domain.audience.Audience.SourceInfo;
import com.unitel.laoads.domain.audience.Audience.VerifyResult;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.LaoadsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Gọi Audience API cho màn tạo tệp khách hàng: danh sách nguồn, bảng, cột, verify. Đổi {@link AudienceException}
 * sang lỗi API: chưa cấu hình → 400 AUDIENCE_NOT_CONFIGURED; request bị service từ chối → 400 VALIDATION_ERROR;
 * mất kết nối / API key sai / service lỗi → 503 AUDIENCE_UNAVAILABLE.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AudienceService {

    private final AudienceClient client;

    public List<SourceInfo> sources() {
        return Arrays.stream(Source.values()).map(s -> new SourceInfo(s, client.configured(s), client.mode())).toList();
    }

    public List<String> tables(Source source) {
        return call(source, () -> client.tables(source).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    /** Cột của bảng, kiểu dữ liệu đã chuẩn hoá về BOOLEAN / INT / LONG / DOUBLE / DATE / STRING. */
    public List<Column> columns(Source source, String table) {
        String t = requireTable(table);
        return call(source, () -> client.columns(source, t).stream()
                .map(c -> new Column(c.name(), DataType.normalize(c.dataType()))).toList());
    }

    /** Kiểm tra cục bộ (400 nếu sai) rồi hỏi service. */
    public VerifyResult verify(Source source, AudienceRequests.RawRequest raw) {
        Request request = AudienceRequests.normalize(raw);
        return call(source, () -> client.verify(source, request));
    }

    /** Dùng khi tạo tệp: request phải hợp lệ cả cục bộ lẫn phía service, trả request đã chuẩn hoá. */
    public Request requireValid(Source source, AudienceRequests.RawRequest raw) {
        Request request = AudienceRequests.normalize(raw);
        VerifyResult r = call(source, () -> client.verify(source, request));
        if (!r.valid()) {
            String msg = r.message() == null || r.message().isBlank() ? "metadata hoặc điều kiện không hợp lệ" : r.message();
            throw LaoadsException.validation(List.of(new FieldViolation("request", "error.validation.audienceInvalid",
                    "Service từ chối request: " + msg, msg)));
        }
        return request;
    }

    public void requireConfigured(Source source) {
        if (!client.configured(source)) {
            throw notConfigured(source);
        }
    }

    <T> T call(Source source, Supplier<T> action) {
        requireConfigured(source);
        try {
            return action.get();
        } catch (AudienceException e) {
            log.warn("[AUDIENCE] {} lỗi {}: {}", source, e.kind(), e.getMessage());
            throw toLaoads(source, e);
        }
    }

    public static LaoadsException toLaoads(Source source, AudienceException e) {
        return switch (e.kind()) {
            case NOT_CONFIGURED -> notConfigured(source);
            case BAD_REQUEST -> LaoadsException.validation(List.of(new FieldViolation("request",
                    "error.validation.audienceInvalid", "Service từ chối request: " + e.getMessage(), e.getMessage())));
            case UNAUTHORIZED -> LaoadsException.withKey(ErrorCode.AUDIENCE_UNAVAILABLE, "error.AUDIENCE_UNAVAILABLE.unauthorized",
                    "Audience API " + source + " từ chối API key", source.name());
            case UNAVAILABLE, PROTOCOL -> new LaoadsException(ErrorCode.AUDIENCE_UNAVAILABLE,
                    "Không kết nối được Audience API " + source + ": " + e.getMessage(), source.name(),
                    String.valueOf(e.getMessage()));
        };
    }

    private static LaoadsException notConfigured(Source source) {
        return new LaoadsException(ErrorCode.AUDIENCE_NOT_CONFIGURED,
                "Nguồn " + source + " chưa được cấu hình (URL / API key)", source.name());
    }

    private static String requireTable(String table) {
        String t = table == null ? null : table.trim();
        if (t == null || !t.matches("^[A-Za-z0-9_.$]{1,128}$")) {
            throw LaoadsException.validation(List.of(new FieldViolation("tableName", "error.validation.invalidValue",
                    "Giá trị không hợp lệ")));
        }
        return t;
    }
}
