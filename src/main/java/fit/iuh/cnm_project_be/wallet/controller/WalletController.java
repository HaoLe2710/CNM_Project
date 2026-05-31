package fit.iuh.cnm_project_be.wallet.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.wallet.dto.response.WalletDeeplinkResponse;
import fit.iuh.cnm_project_be.wallet.dto.response.WalletOverviewResponse;
import fit.iuh.cnm_project_be.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/overview")
    public ApiResponse<WalletOverviewResponse> getOverview() {
        return ApiResponse.ok(walletService.getOverview(), UUID.randomUUID().toString());
    }

    @GetMapping("/deeplink")
    public ApiResponse<WalletDeeplinkResponse> getDeeplink(
            @RequestParam(defaultValue = "OPEN") String action
    ) {
        return ApiResponse.ok(walletService.getDeeplink(action), UUID.randomUUID().toString());
    }
}
