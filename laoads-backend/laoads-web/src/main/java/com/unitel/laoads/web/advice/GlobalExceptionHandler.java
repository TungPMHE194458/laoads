package com.unitel.laoads.web.advice;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.unitel.laoads.api.common.ApiFieldError;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Map exception → envelope lỗi (contract §1). Adapt từ BOC GlobalExceptionHandler (ProblemDetail → ApiResponse).
 *
 * <table>
 *   <tr><td>LaoadsException</td><td>status theo ErrorCode</td><td>mã nghiệp vụ</td></tr>
 *   <tr><td>MethodArgumentNotValid / ConstraintViolation / HandlerMethodValidation</td><td>400</td><td>VALIDATION_ERROR</td></tr>
 *   <tr><td>HttpMessageNotReadable (sai kiểu/enum)</td><td>400</td><td>VALIDATION_ERROR (field)</td></tr>
 *   <tr><td>HttpMessageNotReadable (JSON hỏng)</td><td>400</td><td>BAD_REQUEST_BODY</td></tr>
 *   <tr><td>MissingServletRequestParameter / TypeMismatch / MissingRequestHeader</td><td>400</td><td>INVALID_PARAM</td></tr>
 *   <tr><td>AccessDenied</td><td>403</td><td>FORBIDDEN</td></tr>
 *   <tr><td>NoResourceFound / NoHandlerFound</td><td>404</td><td>NOT_FOUND</td></tr>
 *   <tr><td>DataIntegrityViolation / OptimisticLocking</td><td>409</td><td>CONFLICT</td></tr>
 *   <tr><td>Không kết nối được DB</td><td>503</td><td>DB_UNAVAILABLE</td></tr>
 *   <tr><td>còn lại</td><td>500</td><td>INTERNAL_ERROR</td></tr>
 * </table>
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final ErrorResponses responses;

    private static Locale locale() {
        return LocaleContextHolder.getLocale();
    }

    @ExceptionHandler(LaoadsException.class)
    public ResponseEntity<ApiResponse<Void>> handleLaoads(LaoadsException ex) {
        if (ex.getCode().httpStatus() >= 500) {
            log.warn("Domain error [{}]: {}", ex.getCode(), ex.getMessage());
        } else {
            log.debug("Domain error [{}]: {}", ex.getCode(), ex.getMessage());
        }
        Locale loc = locale();
        String message = responses.message(ex.getMessageKey(), ex.getArgs(), ex.getMessage(), loc);
        List<ApiFieldError> errors = ex.getErrors().isEmpty() ? null : responses.resolve(ex.getErrors(), loc);
        return responses.entity(ex.getCode(), message, errors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiFieldError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.add(new ApiFieldError(e.getField(), String.valueOf(e.getDefaultMessage()))));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.add(new ApiFieldError(e.getObjectName(), String.valueOf(e.getDefaultMessage()))));
        return validation(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        List<ApiFieldError> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(e -> errors.add(
                new ApiFieldError(r.getMethodParameter().getParameterName(), String.valueOf(e.getDefaultMessage())))));
        return validation(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiFieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ApiFieldError(String.valueOf(v.getPropertyPath()), v.getMessage()))
                .toList();
        return validation(errors);
    }

    /** Sai kiểu dữ liệu / enum lạ → VALIDATION_ERROR kèm field; JSON hỏng → BAD_REQUEST_BODY. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.debug("Bad request body: {}", ex.getMessage());
        if (ex.getCause() instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            String field = fieldPath(mie);
            String msg = responses.message("error.validation.invalidValue", null, "Giá trị không hợp lệ", locale());
            return validation(List.of(new ApiFieldError(field, msg)));
        }
        return responses.entity(ErrorCode.BAD_REQUEST_BODY, locale());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        String msg = responses.message("error.INVALID_PARAM.missing", new Object[]{ex.getParameterName()},
                "Thiếu tham số bắt buộc " + ex.getParameterName(), locale());
        return responses.entity(ErrorCode.INVALID_PARAM, msg, null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        String msg = responses.message("error.INVALID_PARAM.missing", new Object[]{ex.getHeaderName()},
                "Thiếu tham số bắt buộc " + ex.getHeaderName(), locale());
        return responses.entity(ErrorCode.INVALID_PARAM, msg, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.debug("Bad query/path param: name={}, value={}", ex.getName(), ex.getValue());
        String msg = ex.getValue() != null
                ? responses.message("error.INVALID_PARAM.withValue", new Object[]{ex.getName(), ex.getValue()},
                "Tham số " + ex.getName() + " không hợp lệ: " + ex.getValue(), locale())
                : responses.message("error.INVALID_PARAM", new Object[]{ex.getName()},
                "Tham số " + ex.getName() + " không hợp lệ", locale());
        return responses.entity(ErrorCode.INVALID_PARAM, msg, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return responses.entity(ErrorCode.FORBIDDEN, locale());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return responses.entity(ErrorCode.UNAUTHORIZED, locale());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoResource(Exception ex) {
        return responses.entity(ErrorCode.NOT_FOUND, locale());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return responses.entity(ErrorCode.METHOD_NOT_ALLOWED, locale());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return responses.entity(ErrorCode.BAD_REQUEST_BODY, locale());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleConflict(Exception ex) {
        log.warn("Data conflict: {}", ex.getMessage());
        return responses.entity(ErrorCode.CONFLICT, locale());
    }

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleDbDown(Exception ex) {
        log.warn("DB unavailable: {}", ex.getMessage());
        return responses.entity(ErrorCode.DB_UNAVAILABLE, locale());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        if (isDbConnectionFailure(ex)) {
            log.warn("DB unavailable: {}", ex.getMessage());
            return responses.entity(ErrorCode.DB_UNAVAILABLE, locale());
        }
        log.error("Unhandled exception", ex);
        return responses.entity(ErrorCode.INTERNAL_ERROR, locale());
    }

    private ResponseEntity<ApiResponse<Void>> validation(List<ApiFieldError> errors) {
        String msg = responses.message(ErrorCode.VALIDATION_ERROR.messageKey(), null,
                "Dữ liệu nhập không hợp lệ", locale());
        return responses.entity(ErrorCode.VALIDATION_ERROR, msg, errors);
    }

    private static String fieldPath(MismatchedInputException mie) {
        StringBuilder sb = new StringBuilder();
        for (var ref : mie.getPath()) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    private static boolean isDbConnectionFailure(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLTransientConnectionException || t instanceof JDBCConnectionException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
