package com.unitel.laoads.web.advice;

import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.workspace.MemberRole;
import com.unitel.laoads.web.config.LocaleConfig;
import com.unitel.laoads.web.filter.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Envelope thành công/lỗi + mapping exception → HTTP/statusCode + i18n theo Accept-Language (không DB). */
class GlobalExceptionHandlerTest {

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponses(ms)))
                .setLocaleResolver(new LocaleConfig().localeResolver())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void successEnvelopeKeepsNullDataAndMessageButOmitsErrorFields() throws Exception {
        mvc.perform(get("/t/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.statusCode").value("OK"))
                .andExpect(jsonPath("$", hasKey("message")))
                .andExpect(jsonPath("$.data", hasKey("fullName")))
                .andExpect(jsonPath("$.data.name").value("BSD"))
                .andExpect(jsonPath("$", not(hasKey("metaData"))))
                .andExpect(jsonPath("$", not(hasKey("requestId"))))
                .andExpect(jsonPath("$", not(hasKey("errors"))));
    }

    @Test
    void nullDataStillSerialized() throws Exception {
        mvc.perform(get("/t/null"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("data")));
    }

    @Test
    void pageEnvelopeHasMetaData() throws Exception {
        mvc.perform(get("/t/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.metaData.totalPages").value(3))
                .andExpect(jsonPath("$.metaData.currentPage").value(1))
                .andExpect(jsonPath("$.metaData.pageSize").value(2))
                .andExpect(jsonPath("$.metaData.totalItems").value(5));
    }

    @Test
    void businessErrorDefaultsToVietnamese() throws Exception {
        mvc.perform(get("/t/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$", hasKey("data")))
                .andExpect(jsonPath("$.statusCode").value("MEMBER_DUPLICATE"))
                .andExpect(jsonPath("$.message").value("Người dùng 'member_bsd' đã là thành viên của workspace"))
                .andExpect(jsonPath("$.requestId", notNullValue()));
    }

    @Test
    void businessErrorTranslatedToEnglishAndLao() throws Exception {
        mvc.perform(get("/t/duplicate").header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(jsonPath("$.message").value("User 'member_bsd' is already a member of this workspace"));
        mvc.perform(get("/t/duplicate").header("Accept-Language", "lo"))
                .andExpect(jsonPath("$.message").value("ຜູ້ໃຊ້ 'member_bsd' ເປັນສະມາຊິກຂອງ workspace ນີ້ແລ້ວ"));
    }

    @Test
    void wrongPasswordIs400NotUnauthorized() throws Exception {
        mvc.perform(get("/t/wrong-password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("WRONG_PASSWORD"));
    }

    @Test
    void beanValidationGivesFieldErrors() throws Exception {
        mvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'role')]").exists());
    }

    @Test
    void invalidEnumIsValidationErrorWithField() throws Exception {
        mvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"role\":\"KING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("role"));
    }

    @Test
    void malformedJsonIsBadRequestBody() throws Exception {
        mvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("BAD_REQUEST_BODY"));
    }

    @Test
    void serviceValidationResolvesFieldMessages() throws Exception {
        mvc.perform(get("/t/service-validation").header("Accept-Language", "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("items[1].actionType"))
                .andExpect(jsonPath("$.errors[0].message").value("Duplicate action type 'ADD_SMS'"));
    }

    @Test
    void typeMismatchAndMissingParamAreInvalidParam() throws Exception {
        mvc.perform(get("/t/typed").param("id", "NaN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("INVALID_PARAM"));
        mvc.perform(get("/t/typed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("INVALID_PARAM"));
    }

    @Test
    void accessDeniedIsForbidden() throws Exception {
        mvc.perform(get("/t/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("FORBIDDEN"));
    }

    @Test
    void workspaceRequiredIs400() throws Exception {
        mvc.perform(get("/t/workspace-required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("WORKSPACE_REQUIRED"));
    }

    @Test
    void dbDownIs503() throws Exception {
        mvc.perform(get("/t/db-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.statusCode").value("DB_UNAVAILABLE"));
    }

    @Test
    void unexpectedIs500() throws Exception {
        mvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.statusCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Lỗi hệ thống — vui lòng thử lại"));
    }

    // ------------------------------------------------------------------ fixture

    public record Sample(String name, String fullName) {
    }

    public record Body(@NotBlank String name, @NotNull MemberRole role) {
    }

    @RestController
    @RequestMapping("/t")
    public static class TestController {

        @GetMapping("/ok")
        public ApiResponse<Sample> ok() {
            return ApiResponse.ok(new Sample("BSD", null));
        }

        @GetMapping("/null")
        public ApiResponse<Sample> nullData() {
            return ApiResponse.ok(null);
        }

        @GetMapping("/page")
        public ApiResponse<List<String>> page() {
            return ApiResponse.page(new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5), s -> s);
        }

        @GetMapping("/duplicate")
        public void duplicate() {
            throw new LaoadsException(ErrorCode.MEMBER_DUPLICATE, "dup", "member_bsd");
        }

        @GetMapping("/wrong-password")
        public void wrongPassword() {
            throw new LaoadsException(ErrorCode.WRONG_PASSWORD, "Mật khẩu hiện tại không đúng");
        }

        @PostMapping("/validate")
        public void validate(@Valid @RequestBody Body body) {
            // chỉ để kích hoạt validation
        }

        @GetMapping("/service-validation")
        public void serviceValidation() {
            throw LaoadsException.validation(List.of(new FieldViolation("items[1].actionType",
                    "error.validation.duplicateActionType", "dup", "ADD_SMS")));
        }

        @GetMapping("/typed")
        public void typed(@RequestParam Long id) {
            // chỉ để kích hoạt binding
        }

        @GetMapping("/denied")
        public void denied() {
            throw new AccessDeniedException("x");
        }

        @GetMapping("/workspace-required")
        public void workspaceRequired() {
            throw new LaoadsException(ErrorCode.WORKSPACE_REQUIRED, "Thiếu header X-Workspace-Id");
        }

        @GetMapping("/db-down")
        public void dbDown() {
            throw new CannotCreateTransactionException("Could not open JPA EntityManager for transaction");
        }

        @GetMapping("/boom")
        public void boom() {
            throw new IllegalStateException("boom");
        }
    }
}
