package com.cachewraith.blog_post_api_spring.config;

import com.cachewraith.blog_post_api_spring.common.response.ApiErrorResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document and the Swagger UI it feeds.
 *
 * <p>The two customizer beans are springdoc's own extension hooks — a decorator over the document
 * and over each generated operation — rather than a pattern introduced here. They exist so the
 * error envelope is documented once instead of being repeated as an annotation on all 35 handlers,
 * where it would drift out of step with {@code GlobalExceptionHandler} the first time anyone forgot
 * one.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";
    private static final String ERROR_SCHEMA = "ApiErrorResponse";

    private static final String DESCRIPTION =
            """
            Auth, profiles, posts with images, feed, comments, reactions, friendships and \
            notifications.

            **Most endpoints need a bearer token.** Get one with `POST /auth/register`, then \
            `POST /auth/verify-otp` with `{email, code}` — then paste the `accessToken` into \
            the green **Authorize** button on the right (value only, no `Bearer` prefix). It \
            survives a page reload.

            The six-digit code is emailed when `spring.mail.host` is set, and otherwise printed \
            in the log as `Local-only REGISTER OTP for <email>: <code>` at DEBUG.

            Success is `{success, data, timestamp}`; every failure is the `default` response \
            listed on each operation, where `code` is the `ErrorCode` name.
            """;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Blog Post API").version("v1").description(DESCRIPTION))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("The accessToken from /auth/login or /auth/verify-otp.")));
    }

    /** Registers the error envelope, which no handler signature mentions and so nothing else pulls in. */
    @Bean
    public OpenApiCustomizer errorSchemaCustomizer() {
        return openApi ->
                ModelConverters.getInstance()
                        .readAll(new AnnotatedType(ApiErrorResponse.class))
                        .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
    }

    /**
     * Documents the error envelope as the {@code default} response on every operation.
     *
     * <p>{@code default} rather than an enumerated 400/401/404 per handler: it means "any status not
     * listed", which is exactly and always true here — {@code GlobalExceptionHandler} answers every
     * failure in this one shape — and needs no per-endpoint guess about which codes a handler can
     * actually raise. A guess is what goes stale.
     */
    @Bean
    public OperationCustomizer errorResponseCustomizer() {
        return (operation, handlerMethod) -> {
            var responses = operation.getResponses();
            if (responses == null || responses.getDefault() != null) {
                return operation;
            }
            responses.addApiResponse(
                    "default",
                    new io.swagger.v3.oas.models.responses.ApiResponse()
                            .description("Error envelope. `code` carries the ErrorCode name.")
                            .content(
                                    new Content()
                                            .addMediaType(
                                                    org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                                                    new MediaType()
                                                            .schema(
                                                                    new Schema<>()
                                                                            .$ref("#/components/schemas/" + ERROR_SCHEMA)))));
            return operation;
        };
    }
}
