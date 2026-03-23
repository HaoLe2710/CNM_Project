package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.dto.response.TokenResponse;
import fit.iuh.cnm_project_be.auth.entity.Account;
import fit.iuh.cnm_project_be.auth.entity.RefreshToken;
import fit.iuh.cnm_project_be.auth.repository.RefreshTokenRepository;
import fit.iuh.cnm_project_be.auth.utils.JwtUtils;
import fit.iuh.cnm_project_be.common.exception.UnauthorizedException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RefreshTokenService {
    RefreshTokenRepository refreshTokenRepository;
    JwtUtils jwtUtils;

    @Transactional
    public RefreshToken saveForAccount(Account account, String token, LocalDateTime expiryDate) {
    refreshTokenRepository.deleteByAccountId(account.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .expiryDate(expiryDate)
                .account(account)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public TokenResponse rotateRefreshToken(String oldTokenStr) {
        RefreshToken oldToken = refreshTokenRepository.findById(oldTokenStr)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is missing or invalid"));

        if (oldToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(oldToken);
            throw new UnauthorizedException("Refresh token was expired. Please make a new login");
        }

        Account account = oldToken.getAccount();

        refreshTokenRepository.delete(oldToken);

        String newAccessToken = jwtUtils.generateToken(account);
        String newRefreshTokenStr = jwtUtils.generateRefreshToken();

        LocalDateTime newExpiry = LocalDateTime.now().plusDays(AuthService.REFRESH_TOKEN_EXPIRE_DAYS);
        saveForAccount(account, newRefreshTokenStr, newExpiry);

        return TokenResponse
                .builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenStr)
                .build();
    }

    @Transactional
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        refreshTokenRepository.deleteById(token);
    }
}
