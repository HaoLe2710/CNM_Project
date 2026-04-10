package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.entity.Account;
import fit.iuh.cnm_project_be.auth.repository.AccountRepository;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.user.service.UserService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service quản lý Account operations
 * - Check if account existed
 * - Soft delete account
 * - Soft delete user and account
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class AccountService {

    AccountRepository accountRepository;
    UserService userService;

    /**
     * Check xem identifier (email/phone) có tồn tại không
     */
    public boolean checkExisted(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new BusinessException("Identifier cannot be empty");
        }

        String value = identifier.trim();
        boolean existed = accountRepository.existsByEmailOrPhone(value, value);
        log.debug("[Account] - Existence check for identifier: {}, result: {}", identifier, existed);
        return existed;
    }

    /**
     * Soft delete account theo userId
     */
    @Transactional
    public void softDeleteAccountByUserId(UUID userId) {
        Account account = accountRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        accountRepository.delete(account);
        log.info("[Account] - Account soft deleted for userId: {}", userId);
    }

    /**
     * Soft delete user và account cùng lúc
     */
    @Transactional
    public void softDeleteUserAndAccount(UUID userId) {
        try {
            userService.softDeleteUser(userId);
            softDeleteAccountByUserId(userId);
            log.info("[Account] - Successfully soft deleted user and account for userId: {}", userId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[Account] - Delete operation failed for userId {}: {}", userId, ex.getMessage());
            throw new BusinessException("Failed to delete user and account");
        }
    }
}
