package com.eduplay.backend.config;

import com.eduplay.backend.common.response.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.info.BuildProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document served at /v3/api-docs (and checked in as docs/openapi.yaml for client code generation).
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI eduplayOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        return new OpenAPI()
                .info(new Info()
                        .title("Eduplay API")
                        .version(build != null ? build.getVersion() : "dev")
                        .description("""
                                REST API for the Eduplay offline-first mathematics game.
                                Obtain tokens from /api/v1/auth/login, then send `Authorization: Bearer <accessToken>`.
                                Access tokens expire after 15 minutes; a 401 with code TOKEN_EXPIRED means the
                                client should call /api/v1/auth/refresh and retry the request.
                                Every error response uses the ApiError schema; switch on its `code` field.
                                Scores are percent correct (0-100). Children, lesson progress and game results carry
                                a gradeId (ecd_a, ecd_b)."""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                // Relative URL: the docs work unchanged behind any host or reverse proxy.
                .servers(List.of(new Server().url("/").description("Current host")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /**
     * Documents the uniform error body: registers the ApiError schema and adds it as the
     * {@code default} response of every operation, so generated clients get a typed error.
     */
    @Bean
    public OpenApiCustomizer apiErrorResponses() {
        return openApi -> {
            ModelConverters.getInstance().readAll(ApiError.class)
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
            ApiResponse error = new ApiResponse()
                    .description("Error. Switch on `code`; the ApiError schema lists every value.")
                    .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))));
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(path -> path.readOperations()
                        .forEach(operation -> operation.getResponses().addApiResponse("default", error)));
            }
        };
    }
}
