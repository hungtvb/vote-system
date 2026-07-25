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

## Voter identity

Registration accepts an optional public `displayName` in addition to `email` and `password`. When it is omitted, the backend creates a privacy-safe pseudonym instead of deriving a public identity from the email address.

Authenticated clients can load the private Voter ID profile with:

```http
GET /api/v1/users/me
Authorization: Bearer <access-token>
```

The response contains the account email, public display name, initials, role, and account timestamps. Ballot feed/detail responses retain the top-level `authorId` for compatibility and also include a public `author` object with only `id`, `displayName`, and `initials`. Author email is never embedded in a public ballot response.

## Source of truth

Controller mappings and request/response DTOs are the source of truth for generated operations and schemas. Global API metadata and JWT security configuration are defined in:

`src/main/java/com/hungtvb/votesystem/common/config/OpenApiConfig.java`
