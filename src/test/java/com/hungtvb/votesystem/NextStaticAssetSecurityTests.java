package com.hungtvb.votesystem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:next-assets;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "management.health.redis.enabled=false",
        "app.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
class NextStaticAssetSecurityTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void servesNextCssWithoutAuthenticationAndWithCssMimeType() throws Exception {
        mockMvc.perform(get("/_next/static/css/asset-probe.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/css")))
                .andExpect(content().string("html { --next-asset-probe: loaded; }\n"));
    }
}
