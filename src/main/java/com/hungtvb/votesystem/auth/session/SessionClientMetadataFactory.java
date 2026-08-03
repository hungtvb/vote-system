package com.hungtvb.votesystem.auth.session;

import com.hungtvb.votesystem.auth.social.SocialProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SessionClientMetadataFactory {

    public SessionClientMetadata password(HttpServletRequest request) {
        return new SessionClientMetadata(SessionProvider.PASSWORD, classify(request));
    }

    public SessionClientMetadata social(SocialProvider provider, HttpServletRequest request) {
        return new SessionClientMetadata(
                switch (provider) {
                    case GOOGLE -> SessionProvider.GOOGLE;
                    case GITHUB -> SessionProvider.GITHUB;
                },
                classify(request)
        );
    }

    String classify(HttpServletRequest request) {
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return "UNKNOWN";
        }

        String normalized = userAgent.toLowerCase(Locale.ROOT);
        String browser = browser(normalized);
        String platform = platform(normalized);
        return browser + " on " + platform;
    }

    private String browser(String userAgent) {
        if (userAgent.contains("edg/")) {
            return "Edge";
        }
        if (userAgent.contains("firefox/")) {
            return "Firefox";
        }
        if (userAgent.contains("crios/") || userAgent.contains("chrome/")) {
            return "Chrome";
        }
        if (userAgent.contains("safari/") && !userAgent.contains("chrome/") && !userAgent.contains("crios/")) {
            return "Safari";
        }
        return "Browser";
    }

    private String platform(String userAgent) {
        if (userAgent.contains("iphone") || userAgent.contains("ipad") || userAgent.contains("ios")) {
            return "iOS";
        }
        if (userAgent.contains("android")) {
            return "Android";
        }
        if (userAgent.contains("windows")) {
            return "Windows";
        }
        if (userAgent.contains("macintosh") || userAgent.contains("mac os")) {
            return "macOS";
        }
        if (userAgent.contains("linux")) {
            return "Linux";
        }
        return "Other";
    }
}
