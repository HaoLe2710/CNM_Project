package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.dto.request.LoginRequest;
import fit.iuh.cnm_project_be.auth.dto.request.RegisterRequest;
import fit.iuh.cnm_project_be.auth.dto.request.ChangePasswordRequest;
import fit.iuh.cnm_project_be.auth.dto.response.LoginResponse;
import fit.iuh.cnm_project_be.auth.dto.response.RegisterResponse;
import fit.iuh.cnm_project_be.auth.entity.Account;
import fit.iuh.cnm_project_be.auth.enums.Role;
import fit.iuh.cnm_project_be.auth.repository.AccountRepository;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.common.exception.UnauthorizedException;
import fit.iuh.cnm_project_be.auth.utils.JwtUtils;
import fit.iuh.cnm_project_be.user.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class AuthService {
    static long REFRESH_TOKEN_EXPIRE_DAYS = 30;

    AccountRepository accountRepository;
    RefreshTokenService refreshTokenService;
    PasswordEncoder passwordEncoder;
    JwtUtils  jwtUtils;
    UserService userService;

    public LoginResponse login(LoginRequest request, HttpServletResponse httpServletResponse) {
        String username = request.getUsername();
        String password = request.getPassword();

        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Username not existed"));

        if (!passwordEncoder.matches(password, account.getPassword())) {
            throw new UnauthorizedException("Wrong password");
        }

        String accessToken = jwtUtils.generateToken(account);
        String refreshToken = jwtUtils.generateRefreshToken();

        LocalDateTime refreshTokenExpiry = LocalDateTime.now().plusDays(REFRESH_TOKEN_EXPIRE_DAYS);
        refreshTokenService.saveForAccount(account, refreshToken, refreshTokenExpiry);

        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", refreshToken)
            .httpOnly(true)
            .secure(false)
            .sameSite("Strict")
            .path("/")
            .maxAge(Duration.ofDays(REFRESH_TOKEN_EXPIRE_DAYS))
            .build();
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        var authorities = account.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toList());

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                account.getUsername(),
                null,
                authorities
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        return LoginResponse.builder()
                .token(accessToken)
                .userName(username)
                .userId(account.getUserId())
                .expiresIn(jwtUtils.getExpiresIn(accessToken))
                .roles(account.getRoles())
                .build();
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        // 1. Lấy token từ Cookie ra
        String refreshToken = Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(cookie -> "refreshToken".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        // 2. Nếu có token, xóa nó khỏi DB
        if (refreshToken != null) {
            refreshTokenService.revokeToken(refreshToken);
        }
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();

        if (accountRepository.existsAccountByUsername(username)) {
            throw new BusinessException("Username already exists");
        }

        UUID userId = UUID.randomUUID();

        try {
            Account newAccount = Account.builder()
                    .username(username)
                    .userId(userId)
                    .password(passwordEncoder.encode(request.getPassword()))
                    .roles(List.of(Role.USER))
                    .build();

            Account savedAccount = accountRepository.save(newAccount);
            userService.createProfileForAccount(savedAccount.getUserId(), savedAccount.getUsername());

            return RegisterResponse.builder()
                    .userId(savedAccount.getUserId())
                    .username(savedAccount.getUsername())
                    .createdAt(savedAccount.getCreatedAt())
                    .build();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Register failed for username {}: {}", username, ex.getMessage(), ex);
            throw new BusinessException("Register failed");
        }
    }

    @Transactional
    public void softDeleteAccountByUserId(UUID userId) {
        Account account = accountRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        accountRepository.delete(account);
    }

    @Transactional
    public void softDeleteUserAndAccount(UUID userId) {
        try {
            userService.softDeleteUser(userId);
            softDeleteAccountByUserId(userId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Delete user/account failed for userId {}: {}", userId, ex.getMessage(), ex);
            throw new BusinessException("Delete user/account failed");
        }
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Account account = getCurrentAccount();

        if (!passwordEncoder.matches(request.getCurrentPassword(), account.getPassword())) {
            throw new UnauthorizedException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), account.getPassword())) {
            throw new BusinessException("New password must be different from current password");
        }

        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
        accountRepository.save(account);
        refreshTokenService.revokeAllForAccount(account.getId());
    }

    private Account getCurrentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException("Unauthenticated");
        }

        String userIdClaim = jwt.getClaimAsString("userId");
        if (userIdClaim != null && !userIdClaim.isBlank()) {
            UUID userId = UUID.fromString(userIdClaim);
            return accountRepository.findByUserIdAndDeletedAtIsNull(userId)
                    .orElseThrow(() -> new NotFoundException("Account not found"));
        }

        String username = jwt.getSubject();
        if (username == null || username.isBlank()) {
            throw new UnauthorizedException("Invalid token claims");
        }

        return accountRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }

}
