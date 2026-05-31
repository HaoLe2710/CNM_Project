package fit.iuh.cnm_project_be.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.user.dto.response.UserSettingsResponse;
import fit.iuh.cnm_project_be.user.entity.UserSetting;
import fit.iuh.cnm_project_be.user.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSettingService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};

    private static final Map<String, Map<String, Object>> DEFAULT_SETTINGS = buildDefaultSettings();
    private static final Set<String> ALLOWED_SECTIONS = DEFAULT_SETTINGS.keySet();

    private final UserSettingRepository userSettingRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public UserSettingsResponse getMySettings() {
        UUID userId = userService.getCurrentUserId();
        List<UserSetting> persistedSettings = userSettingRepository.findByUserId(userId);
        return buildResponse(persistedSettings);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMySection(String section) {
        UUID userId = userService.getCurrentUserId();
        return getSection(userId, section);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSection(UUID userId, String section) {
        String normalizedSection = normalizeSection(section);
        Map<String, Object> defaults = cloneDefaults();
        @SuppressWarnings("unchecked")
        Map<String, Object> baseSection = (Map<String, Object>) defaults.get(normalizedSection);

        return userSettingRepository.findByUserIdAndKey(userId, normalizedSection)
                .map(setting -> deepMerge(baseSection, readJsonObject(setting.getValue())))
                .orElseGet(() -> objectMapper.convertValue(baseSection, MAP_TYPE_REFERENCE));
    }

    @Transactional
    public UserSettingsResponse updateMySettings(Map<String, Object> partialSettings) {
        if (partialSettings == null || partialSettings.isEmpty()) {
            throw new BusinessException("Settings payload is required");
        }

        UUID userId = userService.getCurrentUserId();
        List<UserSetting> existingSettings = userSettingRepository.findByUserId(userId);
        Map<String, UserSetting> existingByKey = new LinkedHashMap<>();
        existingSettings.forEach(setting -> existingByKey.put(setting.getKey(), setting));

        Instant now = Instant.now();
        for (Map.Entry<String, Object> entry : partialSettings.entrySet()) {
            String section = normalizeSection(entry.getKey());
            Map<String, Object> sectionValue = normalizeSectionValue(section, entry.getValue());

            UserSetting setting = existingByKey.get(section);
            if (setting == null) {
                setting = new UserSetting();
                setting.setUserId(userId);
                setting.setKey(section);
            }

            setting.setValue(writeJson(sectionValue));
            setting.setUpdatedAt(now);
            userSettingRepository.save(setting);
            existingByKey.put(section, setting);
        }

        return buildResponse(List.copyOf(existingByKey.values()));
    }

    @Transactional
    public Map<String, Object> updateMySection(String section, Map<String, Object> partialSection) {
        UUID userId = userService.getCurrentUserId();
        return updateSection(userId, section, partialSection);
    }

    @Transactional
    public Map<String, Object> updateSection(UUID userId, String section, Map<String, Object> partialSection) {
        String normalizedSection = normalizeSection(section);
        Map<String, Object> normalizedValue = normalizeSectionValue(normalizedSection, partialSection);
        Map<String, Object> currentSection = getSection(userId, normalizedSection);
        Map<String, Object> mergedSection = deepMerge(currentSection, normalizedValue);

        UserSetting setting = userSettingRepository.findByUserIdAndKey(userId, normalizedSection)
                .orElseGet(() -> {
                    UserSetting createdSetting = new UserSetting();
                    createdSetting.setUserId(userId);
                    createdSetting.setKey(normalizedSection);
                    return createdSetting;
                });

        setting.setValue(writeJson(mergedSection));
        setting.setUpdatedAt(Instant.now());
        userSettingRepository.save(setting);
        return mergedSection;
    }

    private UserSettingsResponse buildResponse(List<UserSetting> persistedSettings) {
        Map<String, Object> mergedSettings = cloneDefaults();
        Instant updatedAt = null;

        for (UserSetting setting : persistedSettings) {
            String section = normalizeSection(setting.getKey());
            if (!ALLOWED_SECTIONS.contains(section)) {
                continue;
            }

            Map<String, Object> persistedSection = readJsonObject(setting.getValue());
            @SuppressWarnings("unchecked")
            Map<String, Object> baseSection = (Map<String, Object>) mergedSettings.get(section);
            mergedSettings.put(section, deepMerge(baseSection, persistedSection));

            if (updatedAt == null || (setting.getUpdatedAt() != null && setting.getUpdatedAt().isAfter(updatedAt))) {
                updatedAt = setting.getUpdatedAt();
            }
        }

        return UserSettingsResponse.builder()
                .settings(mergedSettings)
                .updatedAt(updatedAt)
                .build();
    }

    private Map<String, Object> cloneDefaults() {
        return objectMapper.convertValue(DEFAULT_SETTINGS, MAP_TYPE_REFERENCE);
    }

    private String normalizeSection(String section) {
        if (section == null || section.isBlank()) {
            throw new BusinessException("Setting section is required");
        }

        String normalizedSection = section.trim();
        if (!ALLOWED_SECTIONS.contains(normalizedSection)) {
            throw new BusinessException("Unsupported setting section: " + normalizedSection);
        }
        return normalizedSection;
    }

    private Map<String, Object> normalizeSectionValue(String section, Object rawValue) {
        if (rawValue == null) {
            throw new BusinessException("Setting section '" + section + "' cannot be null");
        }

        if (!(rawValue instanceof Map<?, ?> rawMap)) {
            throw new BusinessException("Setting section '" + section + "' must be an object");
        }

        Map<String, Object> normalizedMap = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            normalizedMap.put(String.valueOf(key), value);
        });
        return normalizedMap;
    }

    private Map<String, Object> readJsonObject(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(json, MAP_TYPE_REFERENCE);
        } catch (Exception ex) {
            throw new BusinessException("Invalid stored user setting data");
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException("Unable to store user settings");
        }
    }

    private Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (base != null) {
            base.forEach((key, value) -> result.put(key, cloneValue(value)));
        }

        if (override == null) {
            return result;
        }

        override.forEach((key, value) -> {
            Object currentValue = result.get(key);
            if (currentValue instanceof Map<?, ?> currentMap && value instanceof Map<?, ?> overrideMap) {
                result.put(
                        key,
                        deepMerge(castToMap(currentMap), castToMap(overrideMap))
                );
            } else {
                result.put(key, cloneValue(value));
            }
        });
        return result;
    }

    private Map<String, Object> castToMap(Map<?, ?> rawMap) {
        Map<String, Object> normalizedMap = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> normalizedMap.put(String.valueOf(key), value));
        return normalizedMap;
    }

    private Object cloneValue(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, Object.class);
    }

    private static Map<String, Map<String, Object>> buildDefaultSettings() {
        Map<String, Map<String, Object>> defaults = new LinkedHashMap<>();
        defaults.put("accountSecurity", mapOf(
                "loginAlerts", true,
                "requireDeviceApproval", true,
                "twoFactorAuthEnabled", false
        ));
        defaults.put("zaloLock", mapOf(
                "enabled", false,
                "method", "NONE",
                "pinConfigured", false,
                "biometricEnabled", false
        ));
        defaults.put("personal", mapOf(
                "saveMediaFromZalo", true
        ));
        defaults.put("zstyle", mapOf(
                "enabled", false,
                "themeId", null,
                "themeName", "Mặc định",
                "backgroundUrl", null,
                "accentColor", "#0068ff"
        ));
        defaults.put("ringback", mapOf(
                "enabled", false,
                "toneId", null,
                "toneName", null,
                "artist", null,
                "previewUrl", null
        ));
        defaults.put("wallet", mapOf(
                "enabled", false,
                "provider", "ZALOPAY",
                "linked", false
        ));
        defaults.put("privacy", mapOf(
                "blockActivity", false,
                "showOnlineStatus", true,
                "readReceipts", true,
                "allowFriendRequests", true
        ));
        defaults.put("dataOnDevice", mapOf(
                "autoDownloadPhotos", true,
                "autoDownloadVideos", false,
                "autoDownloadFiles", true,
                "storageSaverEnabled", false
        ));
        defaults.put("backupAndRestore", mapOf(
                "autoBackup", false,
                "backupOnMobileData", false
        ));
        defaults.put("notifications", mapOf(
                "pushEnabled", true,
                "previewEnabled", true,
                "soundEnabled", true,
                "vibrateEnabled", true
        ));
        defaults.put("messages", mapOf(
                "enterToSend", false,
                "swipeActionsEnabled", true,
                "linkPreviewEnabled", true
        ));
        defaults.put("calls", mapOf(
                "incomingCallNotifications", true,
                "vibrateOnIncomingCall", true
        ));
        defaults.put("timeline", mapOf(
                "showFriendUpdates", true,
                "allowTaggedPosts", true
        ));
        defaults.put("contacts", mapOf(
                "syncPhoneContacts", true,
                "suggestFriendsFromContacts", true,
                "lastSyncedAt", null,
                "searchableByPhone", true,
                "searchableByEmail", true
        ));
        defaults.put("appearance", mapOf(
                "theme", "SYSTEM",
                "themeColor", "#f4f3f3",
                "language", "vi",
                "fontScale", "NORMAL"
        ));
        return defaults;
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(Objects.toString(values[i]), values[i + 1]);
        }
        return map;
    }
}
