package com.hungtvb.votesystem.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityValidatorTests {

    @Test
    void acceptsExplicitHttpsOriginsAndSecurePrefixedCookies() {
        assertThatCode(() -> new ProductionSecurityValidator(
                jwt("uV9#qL2!xR7@pN4$kM8&dF3*wC6-zA1_B5+sJ0=eH7%tY2!rQ9#nP4@v"),
                refresh("__Secure-vote_refresh", true, "Strict"),
                new CorsProperties(List.of("https://app.ballotbox.io.vn")),
                secureEnvironment()
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsCommittedDevelopmentJwtSecret() {
        assertThatThrownBy(() -> new ProductionSecurityValidator(
                jwt("dev-only-change-me-0123456789abcdef"),
                refresh("__Secure-vote_refresh", true, "Strict"),
                new CorsProperties(List.of("https://app.ballotbox.io.vn")),
                secureEnvironment()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret");
    }

    @Test
    void rejectsInsecureRefreshCookie() {
        assertThatThrownBy(() -> new ProductionSecurityValidator(
                jwt("uV9#qL2!xR7@pN4$kM8&dF3*wC6-zA1_B5+sJ0=eH7%tY2!rQ9#nP4@v"),
                refresh("vote_refresh", false, "Lax"),
                new CorsProperties(List.of("https://app.ballotbox.io.vn")),
                secureEnvironment()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Secure");
    }

    @Test
    void rejectsWildcardOrNonHttpsCorsOrigin() {
        assertThatThrownBy(() -> new ProductionSecurityValidator(
                jwt("uV9#qL2!xR7@pN4$kM8&dF3*wC6-zA1_B5+sJ0=eH7%tY2!rQ9#nP4@v"),
                refresh("__Secure-vote_refresh", true, "Strict"),
                new CorsProperties(List.of("http://*.example.com")),
                secureEnvironment()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS");
    }

    @Test
    void rejectsCorsUrlWithPathQueryOrCredentials() {
        assertThatThrownBy(() -> new ProductionSecurityValidator(
                jwt("uV9#qL2!xR7@pN4$kM8&dF3*wC6-zA1_B5+sJ0=eH7%tY2!rQ9#nP4@v"),
                refresh("__Secure-vote_refresh", true, "Strict"),
                new CorsProperties(List.of("https://user@app.ballotbox.io.vn/path?preview=true")),
                secureEnvironment()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS");
    }

    private JwtProperties jwt(String secret) {
        return new JwtProperties("vote-system", secret, Duration.ofMinutes(15));
    }

    private RefreshTokenProperties refresh(String name, boolean secure, String sameSite) {
        return new RefreshTokenProperties(Duration.ofDays(30), name, secure, sameSite);
    }

    private MockEnvironment secureEnvironment() {
        return new MockEnvironment()
                .withProperty("server.servlet.session.cookie.secure", "true")
                .withProperty("server.servlet.session.cookie.name", "__Secure-vote_oauth");
    }
}
