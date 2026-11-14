package com.unitel.laoads.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope chung (docs/API_CONTRACT_V1.md §1 — giữ đúng định dạng SRS LaoAds).
 * <ul>
 *   <li>Luôn có: {@code success}, {@code data} (kể cả null), {@code message} (kể cả null), {@code statusCode}.</li>
 *   <li>Chỉ khi có giá trị: {@code metaData} (danh sách phân trang), {@code requestId}, {@code errors} (lỗi).</li>
 * </ul>
 * Controller trả envelope tường minh; lỗi do GlobalExceptionHandler / EnvelopeAuthHandler dựng.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"success", "data", "message", "statusCode", "metaData", "requestId", "errors"})
public record ApiResponse<T>(
        boolean success,
        @JsonInclude(JsonInclude.Include.ALWAYS) T data,
        @JsonInclude(JsonInclude.Include.ALWAYS) String message,
        String statusCode,
        PageMeta metaData,
        String requestId,
        List<ApiFieldError> errors
) {

    public static final String OK = "OK";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, OK, null, null, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, OK, null, null, null);
    }

    public static <S, T> ApiResponse<List<T>> page(Page<S> page, Function<S, T> mapper) {
        List<T> content = page.getContent().stream().map(mapper).toList();
        return new ApiResponse<>(true, content, null, OK, PageMeta.of(page), null, null);
    }

    public static ApiResponse<Void> error(String statusCode, String message, String requestId,
                                          List<ApiFieldError> errors) {
        return new ApiResponse<>(false, null, message, statusCode, null, requestId,
                errors == null || errors.isEmpty() ? null : errors);
    }
}
