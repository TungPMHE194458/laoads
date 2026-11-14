package com.unitel.laoads.web.advice;

import com.unitel.laoads.api.common.ApiFieldError;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.web.filter.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Dựng envelope lỗi dùng chung cho GlobalExceptionHandler (trong DispatcherServlet) và
 * EnvelopeAuthHandler (filter chain Security). Message tra {@code messages_*.properties} theo locale.
 */
@Component
@RequiredArgsConstructor
public class ErrorResponses {

    private final MessageSource messages;

    public String message(String key, Object[] args, String fallback, Locale locale) {
        String m = messages.getMessage(key, args == null || args.length == 0 ? null : args, null, locale);
        return m != null ? m : fallback;
    }

    public ApiResponse<Void> body(ErrorCode code, String message, List<ApiFieldError> errors) {
        return ApiResponse.error(code.name(), message, MDC.get(RequestIdFilter.MDC_KEY), errors);
    }

    public ResponseEntity<ApiResponse<Void>> entity(ErrorCode code, String message, List<ApiFieldError> errors) {
        return ResponseEntity.status(code.httpStatus()).body(body(code, message, errors));
    }

    public ResponseEntity<ApiResponse<Void>> entity(ErrorCode code, Locale locale) {
        return entity(code, message(code.messageKey(), null, code.name(), locale), null);
    }

    public List<ApiFieldError> resolve(List<FieldViolation> violations, Locale locale) {
        return violations.stream()
                .map(v -> new ApiFieldError(v.field(), message(v.messageKey(), v.args(), v.defaultMessage(), locale)))
                .toList();
    }
}
