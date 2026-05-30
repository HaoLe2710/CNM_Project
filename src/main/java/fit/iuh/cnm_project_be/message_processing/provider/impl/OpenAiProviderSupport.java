package fit.iuh.cnm_project_be.message_processing.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.common.exception.BusinessException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class OpenAiProviderSupport {

    private static final Set<String> SUPPORTED_STT_EXTENSIONS = Set.of(
            "mp3", "mp4", "mpeg", "mpga", "m4a", "wav", "webm", "ogg", "flac");

    private OpenAiProviderSupport() {
    }

    public static URI resolveUri(String baseUrl, String path) {
        String normalizedBaseUrl = trimToNull(baseUrl);
        if (normalizedBaseUrl == null) {
            throw new BusinessException("OpenAI base URL is not configured");
        }
        String prefix = normalizedBaseUrl.replaceAll("/+$", "");
        return URI.create(prefix + path);
    }

    public static String resolveErrorMessage(ObjectMapper objectMapper, int statusCode, String rawBody, String fallback) {
        String message = trimToNull(rawBody);
        if (message != null && objectMapper != null) {
            try {
                JsonNode root = objectMapper.readTree(message);
                String nested = trimToNull(root.path("error").path("message").asText(null));
                if (nested != null) {
                    return "OpenAI API error (" + statusCode + "): " + nested;
                }
            } catch (Exception ignored) {
                // no-op
            }
        }
        if (message != null) {
            return "OpenAI API error (" + statusCode + "): " + message;
        }
        return fallback + " (" + statusCode + ")";
    }

    public static String sanitizeAudioExtension(String fileName, String audioFormat, String mimeType) {
        String byName = extensionFromFileName(fileName);
        if (byName != null) {
            return normalizeAudioAlias(byName);
        }

        String byFormat = normalizeToken(audioFormat);
        if (byFormat != null) {
            return normalizeAudioAlias(byFormat);
        }

        String byMime = extensionFromMimeType(mimeType);
        if (byMime != null) {
            return normalizeAudioAlias(byMime);
        }

        return "mp3";
    }

    public static void validateSttAudioFormat(String fileName, String audioFormat, String mimeType) {
        String extension = sanitizeAudioExtension(fileName, audioFormat, mimeType);
        if (!isSupportedAudioExtension(extension)) {
            throw new BusinessException("Định dạng âm thanh chưa được hỗ trợ.");
        }
    }

    public static MultipartPayload buildMultipartPayload(
            String boundary,
            List<FormField> fields,
            String filePartName,
            String fileName,
            String fileContentType,
            byte[] fileBytes) {
        StringBuilder builder = new StringBuilder(512);
        for (FormField field : fields) {
            if (field == null || trimToNull(field.name()) == null || trimToNull(field.value()) == null) {
                continue;
            }
            builder.append("--").append(boundary).append("\r\n");
            builder.append("Content-Disposition: form-data; name=\"").append(field.name()).append("\"\r\n\r\n");
            builder.append(field.value()).append("\r\n");
        }

        builder.append("--").append(boundary).append("\r\n");
        builder.append("Content-Disposition: form-data; name=\"")
                .append(filePartName)
                .append("\"; filename=\"")
                .append(fileName)
                .append("\"\r\n");
        builder.append("Content-Type: ").append(fileContentType).append("\r\n\r\n");

        byte[] prefixBytes = builder.toString().getBytes(StandardCharsets.UTF_8);
        byte[] suffixBytes = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefixBytes.length + fileBytes.length + suffixBytes.length];
        System.arraycopy(prefixBytes, 0, body, 0, prefixBytes.length);
        System.arraycopy(fileBytes, 0, body, prefixBytes.length, fileBytes.length);
        System.arraycopy(suffixBytes, 0, body, prefixBytes.length + fileBytes.length, suffixBytes.length);

        return new MultipartPayload(body, "multipart/form-data; boundary=" + boundary);
    }

    public static String newBoundary() {
        return "----CNMBoundary" + UUID.randomUUID();
    }

    public static List<FormField> listOf(FormField... fields) {
        List<FormField> items = new ArrayList<>();
        if (fields == null) {
            return items;
        }
        for (FormField field : fields) {
            if (field != null) {
                items.add(field);
            }
        }
        return items;
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String extensionFromFileName(String fileName) {
        String normalized = trimToNull(fileName);
        if (normalized == null || !normalized.contains(".")) {
            return null;
        }
        return normalizeToken(normalized.substring(normalized.lastIndexOf('.') + 1));
    }

    private static boolean isSupportedAudioExtension(String extension) {
        String normalized = normalizeToken(extension);
        return normalized != null && SUPPORTED_STT_EXTENSIONS.contains(normalized);
    }

    private static String normalizeAudioAlias(String extension) {
        String normalized = normalizeToken(extension);
        if (normalized == null) {
            return null;
        }
        if ("mpeg".equals(normalized) || "mpga".equals(normalized)) {
            return "mp3";
        }
        if ("x-wav".equals(normalized)) {
            return "wav";
        }
        if ("x-flac".equals(normalized)) {
            return "flac";
        }
        return normalized;
    }

    private static String extensionFromMimeType(String mimeType) {
        String normalized = normalizeToken(mimeType);
        if (normalized == null) {
            return null;
        }
        int semicolonIndex = normalized.indexOf(';');
        String contentType = semicolonIndex >= 0 ? normalized.substring(0, semicolonIndex).trim() : normalized;
        if (contentType.isBlank()) {
            return null;
        }

        if ("audio/mpeg".equals(contentType) || "audio/mp3".equals(contentType)) {
            return "mp3";
        }
        if ("audio/mp4".equals(contentType) || "audio/m4a".equals(contentType) || "audio/x-m4a".equals(contentType)) {
            return "m4a";
        }
        if ("audio/wav".equals(contentType) || "audio/x-wav".equals(contentType)) {
            return "wav";
        }
        if ("audio/ogg".equals(contentType) || "audio/opus".equals(contentType)) {
            return "ogg";
        }
        if ("audio/webm".equals(contentType)) {
            return "webm";
        }
        if ("audio/flac".equals(contentType) || "audio/x-flac".equals(contentType)) {
            return "flac";
        }

        if (contentType.startsWith("audio/")) {
            return contentType.substring("audio/".length());
        }
        return null;
    }

    private static String normalizeToken(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public record FormField(String name, String value) {
    }

    public record MultipartPayload(byte[] body, String contentType) {
    }
}
