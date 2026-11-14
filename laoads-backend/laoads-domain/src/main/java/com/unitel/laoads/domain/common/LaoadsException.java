package com.unitel.laoads.domain.common;

import lombok.Getter;

import java.util.List;

/**
 * Exception nghiệp vụ duy nhất của LaoAds. GlobalExceptionHandler map sang envelope:
 * HTTP status = {@link ErrorCode#httpStatus()}, statusCode = {@link ErrorCode#name()},
 * message = dịch {@link #getMessageKey()} theo Accept-Language (fallback {@link #getMessage()} — tiếng Việt).
 */
@Getter
public class LaoadsException extends RuntimeException {

    private final ErrorCode code;
    private final String messageKey;
    private final transient Object[] args;
    private final transient List<FieldViolation> errors;

    public LaoadsException(ErrorCode code, String defaultMessage, Object... args) {
        this(code, code.messageKey(), defaultMessage, List.of(), args);
    }

    private LaoadsException(ErrorCode code, String messageKey, String defaultMessage,
                            List<FieldViolation> errors, Object... args) {
        super(defaultMessage);
        this.code = code;
        this.messageKey = messageKey;
        this.args = args == null ? new Object[0] : args;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** Dùng message key riêng (khác {@code error.<MÃ>}) nhưng giữ nguyên statusCode. */
    public static LaoadsException withKey(ErrorCode code, String messageKey, String defaultMessage,
                                          Object... args) {
        return new LaoadsException(code, messageKey, defaultMessage, List.of(), args);
    }

    /** 400 VALIDATION_ERROR kèm danh sách field lỗi. */
    public static LaoadsException validation(List<FieldViolation> errors) {
        return new LaoadsException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.messageKey(),
                "Dữ liệu nhập không hợp lệ", errors);
    }

    /** 404 NOT_FOUND với message key cụ thể theo loại đối tượng, vd {@code error.notFound.workspace}. */
    public static LaoadsException notFound(String messageKey, String defaultMessage, Object... args) {
        return new LaoadsException(ErrorCode.NOT_FOUND, messageKey, defaultMessage, List.of(), args);
    }
}
