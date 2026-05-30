package fit.iuh.cnm_project_be.linkpreview.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.linkpreview.dto.LinkPreviewResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class LinkPreviewService {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_HTML_BYTES = 256 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern META_PATTERN = Pattern.compile(
            "<meta\\s+([^>]*?)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern ATTR_PATTERN = Pattern.compile(
            "([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>/]+))",
            Pattern.CASE_INSENSITIVE
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public LinkPreviewResponse extract(String rawUrl) {
        URI uri = parseAndValidate(rawUrl);
        try {
            return fetch(uri, 0);
        } catch (IOException ex) {
            log.debug("Could not fetch link preview for host {}: {}", safeHost(uri), ex.getMessage());
            return fallback(uri);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return fallback(uri);
        }
    }

    private LinkPreviewResponse fetch(URI uri, int redirectCount) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "CNM-LinkPreviewBot/1.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status >= 300 && status < 400) {
            if (redirectCount >= MAX_REDIRECTS) {
                return fallback(uri);
            }
            Optional<String> location = response.headers().firstValue("location");
            if (location.isEmpty()) {
                return fallback(uri);
            }
            URI redirected = parseAndValidate(uri.resolve(location.get()).toString());
            return fetch(redirected, redirectCount + 1);
        }

        if (status < 200 || status >= 300) {
            return fallback(uri);
        }

        String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
        if (!contentType.isBlank() && !contentType.contains("text/html") && !contentType.contains("application/xhtml")) {
            return fallback(uri);
        }

        byte[] bytes;
        try (InputStream body = response.body()) {
            bytes = body.readNBytes(MAX_HTML_BYTES);
        }
        String html = new String(bytes, StandardCharsets.UTF_8);
        return parseHtml(uri, html);
    }

    private LinkPreviewResponse parseHtml(URI uri, String html) {
        String title = firstNonBlank(
                meta(html, "property", "og:title"),
                meta(html, "name", "twitter:title"),
                title(html)
        );
        String description = firstNonBlank(
                meta(html, "property", "og:description"),
                meta(html, "name", "description"),
                meta(html, "name", "twitter:description")
        );
        String image = firstNonBlank(
                meta(html, "property", "og:image"),
                meta(html, "name", "twitter:image")
        );
        String canonicalUrl = firstNonBlank(meta(html, "property", "og:url"), uri.toString());

        return new LinkPreviewResponse(
                clean(title),
                clean(description),
                absolutize(uri, clean(image)),
                canonicalUrl,
                safeHost(uri)
        );
    }

    private URI parseAndValidate(String rawUrl) {
        String normalized = String.valueOf(rawUrl == null ? "" : rawUrl).trim();
        if (normalized.isBlank()) {
            throw new BusinessException("URL is required");
        }

        URI uri;
        try {
            uri = UriComponentsBuilder.fromUriString(normalized).build(true).toUri();
        } catch (RuntimeException ex) {
            throw new BusinessException("URL is invalid");
        }

        String scheme = Optional.ofNullable(uri.getScheme()).orElse("").toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BusinessException("Only HTTP/HTTPS URLs are supported");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException("URL host is invalid");
        }

        validatePublicHost(host);
        return uri;
    }

    private void validatePublicHost(String host) {
        String asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        if (asciiHost.equals("localhost") || asciiHost.endsWith(".localhost")) {
            throw new BusinessException("Local URLs are not allowed");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(asciiHost)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new BusinessException("Private network URLs are not allowed");
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            // A public-looking host that does not resolve is not a bad client request for chat UI.
            // The actual fetch will fail and return a harmless fallback preview instead.
            log.debug("Link preview host could not be resolved: {}", asciiHost);
        }
    }

    private String meta(String html, String keyAttribute, String keyValue) {
        Matcher matcher = META_PATTERN.matcher(html);
        while (matcher.find()) {
            String attributes = matcher.group(1);
            String key = attr(attributes, keyAttribute);
            if (keyValue.equalsIgnoreCase(key)) {
                return attr(attributes, "content");
            }
        }
        return "";
    }

    private String attr(String attributes, String name) {
        Matcher matcher = ATTR_PATTERN.matcher(attributes);
        while (matcher.find()) {
            if (name.equalsIgnoreCase(matcher.group(1))) {
                return firstNonBlank(matcher.group(3), matcher.group(4), matcher.group(5));
            }
        }
        return "";
    }

    private String title(String html) {
        Matcher matcher = TITLE_PATTERN.matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }

    private LinkPreviewResponse fallback(URI uri) {
        return new LinkPreviewResponse("", "", "", uri.toString(), safeHost(uri));
    }

    private String absolutize(URI base, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return base.resolve(value).toString();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String clean(String value) {
        return String.valueOf(value == null ? "" : value)
                .replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safeHost(URI uri) {
        return Optional.ofNullable(uri.getHost()).orElse(uri.toString());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
