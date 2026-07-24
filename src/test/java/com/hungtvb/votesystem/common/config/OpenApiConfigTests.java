package com.hungtvb.votesystem.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTests {

    @Test
    void exposesApiMetadataAndJwtBearerScheme() {
        OpenAPI openAPI = new OpenApiConfig().voteSystemOpenApi();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Vote System API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1");
        assertThat(openAPI.getComponents().getSecuritySchemes())
                .containsKey("bearerAuth");

        SecurityScheme bearer = openAPI.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(bearer.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(bearer.getScheme()).isEqualTo("bearer");
        assertThat(bearer.getBearerFormat()).isEqualTo("JWT");
        assertThat(openAPI.getSecurity()).anySatisfy(requirement ->
                assertThat(requirement).containsKey("bearerAuth"));
    }
}
