package fit.iuh.cnm_project_be.webrtc.service;

import fit.iuh.cnm_project_be.webrtc.config.WebRtcSignalProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class WebRtcSignalUrlResolver {

    private static final String DEFAULT_SIGNAL_URL = "ws://127.0.0.1:8080/signal";
    private static final String SCHEME_REGEX = "^[a-zA-Z][a-zA-Z0-9+\\-.]*://.*";

    private final WebRtcSignalProperties properties;

    public WebRtcSignalUrlResolver(WebRtcSignalProperties properties) {
        this.properties = properties;
    }

    public String resolvePublicSignalUrl() {
        return normalize(properties.getSignalPublicUrl());
    }

    public String normalize(String configuredUrl) {
        String source = hasText(configuredUrl) ? configuredUrl.trim() : DEFAULT_SIGNAL_URL;
        String withScheme = source.matches(SCHEME_REGEX) ? source : "ws://" + source;

        try {
            URI parsed = new URI(withScheme);
            String scheme = parsed.getScheme();
            String mappedScheme = switchScheme(scheme);

            URI normalized = parsed;
            if (!mappedScheme.equalsIgnoreCase(scheme)) {
                normalized = new URI(
                        mappedScheme,
                        parsed.getUserInfo(),
                        parsed.getHost(),
                        parsed.getPort(),
                        parsed.getPath(),
                        parsed.getQuery(),
                        parsed.getFragment()
                );
            }

            String normalizedText = normalized.toString();
            return normalizedText.endsWith("/") && normalizedText.length() > 1
                    ? normalizedText.substring(0, normalizedText.length() - 1)
                    : normalizedText;
        } catch (URISyntaxException error) {
            return withScheme.endsWith("/") && withScheme.length() > 1
                    ? withScheme.substring(0, withScheme.length() - 1)
                    : withScheme;
        }
    }

    private String switchScheme(String scheme) {
        if (!hasText(scheme)) {
            return "ws";
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return "ws";
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return "wss";
        }
        return scheme;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
