package fit.iuh.cnm_project_be.wallet.service;

import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.service.UserService;
import fit.iuh.cnm_project_be.user.service.UserSettingService;
import fit.iuh.cnm_project_be.wallet.dto.response.WalletDeeplinkResponse;
import fit.iuh.cnm_project_be.wallet.dto.response.WalletOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private static final String SECTION = "wallet";

    private final UserSettingService userSettingService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public WalletOverviewResponse getOverview() {
        Map<String, Object> section = userSettingService.getMySection(SECTION);
        UserProfile profile = userService.getMyProfile();
        return WalletOverviewResponse.builder()
                .enabled(asBoolean(section.get("enabled")))
                .provider(asString(section.get("provider")))
                .linked(asBoolean(section.get("linked")))
                .maskedPhone(maskPhone(profile.getPhone()))
                .balance(null)
                .currency("VND")
                .build();
    }

    @Transactional(readOnly = true)
    public WalletDeeplinkResponse getDeeplink(String action) {
        Map<String, Object> section = userSettingService.getMySection(SECTION);
        UUID userId = userService.getCurrentUserId();
        String normalizedAction = normalizeAction(action);
        String provider = asString(section.get("provider"));
        String query = "action=" + encode(normalizedAction) + "&userId=" + encode(userId.toString());
        return WalletDeeplinkResponse.builder()
                .provider(provider)
                .action(normalizedAction)
                .deeplink("zalopay://cnm-project?" + query)
                .fallbackUrl("https://zalopay.vn/?" + query)
                .build();
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "OPEN";
        }
        return action.trim().toUpperCase(Locale.ROOT);
    }

    private boolean asBoolean(Object value) {
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return phone;
        }
        return "******" + phone.substring(phone.length() - 4);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
