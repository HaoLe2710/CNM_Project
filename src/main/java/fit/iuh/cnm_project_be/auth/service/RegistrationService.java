package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.dto.request.RegisterRequest;
import fit.iuh.cnm_project_be.auth.dto.response.RegisterResponse;
import fit.iuh.cnm_project_be.auth.entity.Account;
import fit.iuh.cnm_project_be.auth.enums.Role;
import fit.iuh.cnm_project_be.auth.repository.AccountRepository;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.user.service.UserService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service xử lý Register logic
 * - Validate account existence
 * - Validate OTP token
 * - Tạo account mới
 * - Tạo user profile
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class RegistrationService {

    AccountRepository accountRepository;
    PasswordEncoder passwordEncoder;
    UserService userService;
    OtpService otpService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String phone = request.getPhone().trim();

        // 1. Validate email không đã tồn tại
        if (accountRepository.existsByEmail(email)) {
            throw new BusinessException("Email already registered");
        }

        // 2. Validate phone number không đã tồn tại
        if (accountRepository.existsByPhone(phone)) {
            throw new BusinessException("Phone already registered");
        }

        // 3. Validate OTP token (đây sẽ xóa token nếu hợp lệ)
        otpService.validateRegisterToken(email, request.getRegisterToken());

        try {
            // 4. Tạo account mới
            UUID userId = UUID.randomUUID();
            Account newAccount = Account.builder()
                    .username(email)
                    .email(email)
                    .phone(phone)
                    .userId(userId)
                    .password(passwordEncoder.encode(request.getPassword()))
                    .roles(List.of(Role.USER))
                    .build();

            Account savedAccount = accountRepository.save(newAccount);

            // 5. Tạo user profile
            userService.createProfileForAccount(
                    savedAccount.getUserId(),
                    savedAccount.getUsername(),
                    savedAccount.getPhone(),
                    request.getFirstName(),
                    request.getLastName(),
                    request.getDob()
            );

            log.info("[Registration] - New account created successfully for username: {}", email);

            return RegisterResponse.builder()
                    .userId(savedAccount.getUserId())
                    .username(savedAccount.getUsername())
                    .createdAt(savedAccount.getCreatedAt())
                    .build();

        } catch (Exception ex) {
            log.error("[Registration] - Registration failed for email {}: {}", email, ex.getMessage());
            throw new BusinessException("Registration failed due to a system error");
        }
    }
}
