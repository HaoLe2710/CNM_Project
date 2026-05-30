package fit.iuh.cnm_project_be.cloud.enums;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum CloudFileType {
    FOLDER,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    ARCHIVE,
    OTHER;

    private static final Set<String> DOCUMENT_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/rtf",
            "text/plain",
            "text/csv",
            "text/markdown"
    );

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md", "rtf"
    );

    private static final Set<String> ARCHIVE_MIME_TYPES = Set.of(
            "application/zip",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            "application/gzip",
            "application/x-tar"
    );

    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of(
            "zip", "rar", "7z", "gz", "tar", "tgz"
    );

    public static Optional<CloudFileType> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(CloudFileType.valueOf(code.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static CloudFileType detect(boolean isFolder, String mimeType, String fileName) {
        if (isFolder) {
            return FOLDER;
        }

        String normalizedMimeType = normalizeMimeType(mimeType);
        if (normalizedMimeType.startsWith("image/")) {
            return IMAGE;
        }
        if (normalizedMimeType.startsWith("video/")) {
            return VIDEO;
        }
        if (normalizedMimeType.startsWith("audio/")) {
            return AUDIO;
        }
        if (normalizedMimeType.startsWith("text/") || DOCUMENT_MIME_TYPES.contains(normalizedMimeType)) {
            return DOCUMENT;
        }
        if (ARCHIVE_MIME_TYPES.contains(normalizedMimeType)) {
            return ARCHIVE;
        }

        String extension = extractExtension(fileName);
        if (DOCUMENT_EXTENSIONS.contains(extension)) {
            return DOCUMENT;
        }
        if (ARCHIVE_EXTENSIONS.contains(extension)) {
            return ARCHIVE;
        }
        return OTHER;
    }

    public static String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int extensionStartIndex = fileName.lastIndexOf('.');
        if (extensionStartIndex < 0 || extensionStartIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(extensionStartIndex + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "";
        }
        return mimeType.trim().toLowerCase(Locale.ROOT);
    }
}
