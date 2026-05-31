package fit.iuh.cnm_project_be.ringback.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.ringback.dto.request.UpdateRingbackRequest;
import fit.iuh.cnm_project_be.ringback.dto.response.RingbackResponse;
import fit.iuh.cnm_project_be.user.service.UserSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RingbackService {

    private static final String SECTION = "ringback";

    private final UserSettingService userSettingService;

    @Transactional(readOnly = true)
    public RingbackResponse getMine() {
        return toResponse(userSettingService.getMySection(SECTION));
    }

    @Transactional
    public RingbackResponse updateMine(UpdateRingbackRequest request) {
        if (request == null) {
            throw new BusinessException("Ringback payload is required");
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        putIfNotNull(updates, "enabled", request.getEnabled());
        putIfNotNull(updates, "toneId", trimToNull(request.getToneId()));
        putIfNotNull(updates, "toneName", trimToNull(request.getToneName()));
        putIfNotNull(updates, "artist", trimToNull(request.getArtist()));
        putIfNotNull(updates, "previewUrl", trimToNull(request.getPreviewUrl()));
        return toResponse(userSettingService.updateMySection(SECTION, updates));
    }

    private RingbackResponse toResponse(Map<String, Object> section) {
        return RingbackResponse.builder()
                .enabled(asBoolean(section.get("enabled")))
                .toneId(asString(section.get("toneId")))
                .toneName(asString(section.get("toneName")))
                .artist(asString(section.get("artist")))
                .previewUrl(asString(section.get("previewUrl")))
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
