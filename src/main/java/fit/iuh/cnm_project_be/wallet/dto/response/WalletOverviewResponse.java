package fit.iuh.cnm_project_be.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WalletOverviewResponse {
    private boolean enabled;
    private String provider;
    private boolean linked;
    private String maskedPhone;
    private Long balance;
    private String currency;
}
