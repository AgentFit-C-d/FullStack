package com.agentfit.coreapi.global.config;

import com.agentfit.coreapi.domain.project.controller.ProjectController;
import com.agentfit.coreapi.recommendation.controller.RecommendationController;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class OpenApiConfig {

    private static final String SESSION_SECURITY_SCHEME = "agentfitSession";
    private static final String CSRF_SECURITY_SCHEME = "agentfitCsrf";

    @Bean
    OpenAPI agentFitOpenApi() {
        return new OpenAPI()
            .openapi("3.0.3")
            .info(new Info()
                .title("AgentFit Core API")
                .version("v1")
                .description("Frontend integration contract. Protected endpoints require the AGENTFIT_SESSION cookie. "
                    + "Swagger UI automatically sends the XSRF-TOKEN cookie as the X-XSRF-TOKEN header for state-changing requests.")
                .license(new License().name("Private")))
            .components(new Components()
                .addSecuritySchemes(SESSION_SECURITY_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("AGENTFIT_SESSION")
                        .description("Authenticated browser session cookie"))
                .addSecuritySchemes(CSRF_SECURITY_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-XSRF-TOKEN")
                        .description("CSRF header mirrored automatically from the XSRF-TOKEN cookie by Swagger UI.")));
    }

    @Bean
    OperationCustomizer operationSecurityCustomizer() {
        return (operation, handlerMethod) -> {
            if (!requiresSession(handlerMethod)) {
                return operation;
            }

            SecurityRequirement requirement = new SecurityRequirement().addList(SESSION_SECURITY_SCHEME);
            if (!handlerMethod.hasMethodAnnotation(GetMapping.class)) {
                requirement.addList(CSRF_SECURITY_SCHEME);
            }
            operation.addSecurityItem(requirement);
            return operation;
        };
    }

    private static boolean requiresSession(HandlerMethod handlerMethod) {
        Class<?> controllerType = handlerMethod.getBeanType();
        return controllerType == ProjectController.class || controllerType == RecommendationController.class;
    }
}
