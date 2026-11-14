package com.unitel.laoads.web.config;

import com.unitel.laoads.api.common.WorkspaceContext;
import com.unitel.laoads.api.common.WorkspaceScoped;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI laoadsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Unitel LaoAds Backend API")
                        .description("LaoAds — nền tảng chiến dịch/quảng cáo Unitel Lào. Envelope ApiResponse "
                                + "(docs/API_CONTRACT_V1.md). Header Accept-Language: vi | en | lo.")
                        .version("v1")
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token từ POST /v1/auth/login (30 phút)")));
    }

    /** Thêm header X-Workspace-Id (🏢) + Accept-Language vào operation tương ứng. */
    @Bean
    public OperationCustomizer workspaceHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            WorkspaceScoped ws = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(),
                    WorkspaceScoped.class);
            if (ws == null) {
                ws = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), WorkspaceScoped.class);
            }
            if (ws != null) {
                operation.addParametersItem(new HeaderParameter()
                        .name(WorkspaceContext.HEADER)
                        .required(ws.required())
                        .description(ws.required()
                                ? "Workspace hiện hành (bắt buộc)"
                                : "Workspace hiện hành (bắt buộc với user không phải SUPER_ADMIN)")
                        .schema(new IntegerSchema().format("int64")));
            }
            operation.addParametersItem(new HeaderParameter()
                    .name("Accept-Language")
                    .required(false)
                    .schema(new StringSchema()._enum(List.of("vi", "en", "lo"))));
            return operation;
        };
    }
}
