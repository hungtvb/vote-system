# Vote System API documentation

The backend generates its OpenAPI specification from the Spring MVC controllers through springdoc-openapi.

## Local URLs

After starting the Spring Boot application on port `8080`:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

The documentation routes are public. API operations that require authentication use the `bearerAuth` HTTP security scheme.

## Authorize Swagger UI

1. Register or log in through an auth endpoint.
2. Copy the returned access token.
3. Open Swagger UI and select **Authorize**.
4. Paste the access token. Swagger UI adds the `Bearer` prefix automatically.

The refresh token is stored in an `HttpOnly` cookie and is not entered into the Swagger authorization dialog.

## Source of truth

Controller mappings and request/response DTOs are the source of truth for generated operations and schemas. Global API metadata and JWT security configuration are defined in:

`src/main/java/com/hungtvb/votesystem/common/config/OpenApiConfig.java`
