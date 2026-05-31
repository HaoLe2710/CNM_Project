package fit.iuh.cnm_project_be.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WalletDeeplinkResponse {
    private String provider;
    private String action;
    private String deeplink;
    private String fallbackUrl;
}
