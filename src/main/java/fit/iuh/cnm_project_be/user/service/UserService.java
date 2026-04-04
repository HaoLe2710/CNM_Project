package fit.iuh.cnm_project_be.user.service;

import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.dto.request.UpdateUserProfileRequest;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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

    @Transactional
    public UserProfile updateUserProfile(UpdateUserProfileRequest request) {
        UserProfile user = getMyProfile();
        user.setDisplayName(request.getDisplayName());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setBio(request.getBio());
        user.setPhone(request.getPhone());
        user.setGender(request.getGender());
        user.setDob(request.getDob());
        return userProfileRepository.save(user);
    }

    @Transactional
    public UserProfile createProfileForAccount(UUID userId, String username) {
        if (userProfileRepository.existsById(userId)) {
            throw new BusinessException("User profile already exists");
        }

        UserProfile userProfile = UserProfile.builder()
                .userId(userId)
                .username(username)
            .displayName(null)
                .build();

        return userProfileRepository.save(userProfile);
    }

    @Transactional(readOnly = true)
    public UserProfile getUserProfile() {
        return getMyProfile();
    }

    @Transactional(readOnly = true)
    public UserProfile getMyProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException("Unauthenticated");
        }

        String userIdClaim = jwt.getClaimAsString("userId");
        if (userIdClaim != null && !userIdClaim.isBlank()) {
            UUID userId = UUID.fromString(userIdClaim);
            return userProfileRepository.findById(userId)
                    .filter(u -> u.getDeletedAt() == null)
                    .orElseThrow(() -> new NotFoundException("User not found"));
        }

        String username = jwt.getSubject();
        if (username == null || username.isBlank()) {
            throw new UnauthorizedException("Invalid token claims");
        }

        return userProfileRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}