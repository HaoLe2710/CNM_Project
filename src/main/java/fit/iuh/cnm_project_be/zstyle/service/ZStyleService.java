package fit.iuh.cnm_project_be.zstyle.service;

import fit.iuh.cnm_project_be.user.service.UserSettingService;
import fit.iuh.cnm_project_be.zstyle.dto.request.UpdateZStyleRequest;
import fit.iuh.cnm_project_be.zstyle.dto.response.ZStyleResponse;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ZStyleService {

    private static final String SECTION = "zstyle";

    private final UserSettingService userSettingService;

    @Transactional(readOnly = true)
    public ZStyleResponse getMine() {
        return toResponse(userSettingService.getMySection(SECTION));
    }

    @Transactional
    public ZStyleResponse updateMine(UpdateZStyleRequest request) {
        if (request == null) {
            throw new BusinessException("ZStyle payload is required");
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        putIfNotNull(updates, "enabled", request.getEnabled());
        putIfNotNull(updates, "themeId", trimToNull(request.getThemeId()));
        putIfNotNull(updates, "themeName", trimToNull(request.getThemeName()));
        putIfNotNull(updates, "backgroundUrl", trimToNull(request.getBackgroundUrl()));
        putIfNotNull(updates, "accentColor", trimToNull(request.getAccentColor()));
        return toResponse(userSettingService.updateMySection(SECTION, updates));
    }

    private ZStyleResponse toResponse(Map<String, Object> section) {
        return ZStyleResponse.builder()
                .enabled(asBoolean(section.get("enabled")))
                .themeId(asString(section.get("themeId")))
                .themeName(asString(section.get("themeName")))
                .backgroundUrl(asString(section.get("backgroundUrl")))
                .accentColor(asString(section.get("accentColor")))
                .build();
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private boolean asBoolean(Object value) {
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
