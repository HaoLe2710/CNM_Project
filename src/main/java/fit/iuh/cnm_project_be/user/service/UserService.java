package fit.iuh.cnm_project_be.user.service;

import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public UserProfile getUser(UUID userId) {
        return userProfileRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public UserProfile updateDisplayName(UUID userId, String newName) {
        UserProfile user = getUser(userId);

        if (newName == null || newName.isBlank()) {
            throw new BusinessException("Display name invalid");
        }

        user.setDisplayName(newName);
        return userProfileRepository.save(user);
    }

    @Transactional
    public void softDeleteUser(UUID userId) {
        UserProfile user = getUser(userId);
        user.setDeletedAt(Instant.now());
        userProfileRepository.save(user);
    }
}