package fit.iuh.cnm_project_be.user.service;

import fit.iuh.cnm_project_be.aws.AwsS3ImageService;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.common.exception.UnauthorizedException;
import fit.iuh.cnm_project_be.user.dto.request.UpdateUserProfileRequest;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final AwsS3ImageService awsS3ImageService;

    @Transactional(readOnly = true)
    public UserProfile getUser(UUID userId) {
        return userProfileRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public UUID getCurrentUserId() {
        UUID userIdFromJwt = resolveUserIdFromJwt();
        if (userIdFromJwt != null) {
            return userIdFromJwt;
        }
        return resolveUserIdFromHeader();
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
    public UserProfile updateProfileCoverImage(MultipartFile imageFile) {
        UserProfile user = getMyProfile();

        String oldCoverUrl = user.getCoverUrl();
        if (oldCoverUrl != null && !oldCoverUrl.isBlank()) {
            awsS3ImageService.deleteImage(oldCoverUrl);
        }

        String newCoverUrl = awsS3ImageService.uploadImage(user.getUserId(), imageFile);
        user.setCoverUrl(newCoverUrl);
        return userProfileRepository.save(user);
    }

    @Transactional
    public UserProfile updateUserProfile(UpdateUserProfileRequest request) {
        UserProfile user = getMyProfile();
        user.setDisplayName(request.getDisplayName());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setBio(request.getBio());
        user.setPhone(request.getPhone());
        user.setGender(request.getGender());
        user.setDob(request.getDob());
        return userProfileRepository.save(user);
    }

    @Transactional
    public UserProfile updateProfileAvatar(MultipartFile imageFile) {
        UserProfile user = getMyProfile();

        String oldAvatarUrl = user.getAvatarUrl();
        if (oldAvatarUrl != null && !oldAvatarUrl.isBlank()) {
            awsS3ImageService.deleteImage(oldAvatarUrl);
        }

        String newAvatarUrl = awsS3ImageService.uploadImage(user.getUserId(), imageFile);
        user.setAvatarUrl(newAvatarUrl);
        return userProfileRepository.save(user);
    }

    @Transactional
    public UserProfile createProfileForAccount(
            UUID userId,
            String username,
            String phone,
            String firstName,
            String lastName,
            LocalDate dob,
            @NotNull(message = "Gender cannot be empty") String gender) {
        if (userProfileRepository.existsById(userId)) {
            throw new BusinessException("User profile already exists");
        }

        UserProfile userProfile = UserProfile.builder()
                .userId(userId)
                .username(username)
                .phone(phone)
                .email(username)
                .firstName(firstName)
                .lastName(lastName)
                .gender(gender)
                .dob(dob)
                .displayName(lastName + " " + firstName)
                .build();

        return userProfileRepository.save(userProfile);
    }

    @Transactional(readOnly = true)
    public UserProfile getUserProfile() {
        return getMyProfile();
    }

    @Transactional(readOnly = true)
    public UserProfile getMyProfile() {
        UUID userIdFromJwt = resolveUserIdFromJwt();
        if (userIdFromJwt != null) {
            return userProfileRepository.findById(userIdFromJwt)
                    .filter(u -> u.getDeletedAt() == null)
                    .orElseThrow(() -> new NotFoundException("User not found"));
        }

        UUID userIdFromHeader = resolveUserIdFromHeader();
        if (userIdFromHeader != null) {
            return userProfileRepository.findById(userIdFromHeader)
                    .filter(u -> u.getDeletedAt() == null)
                    .orElseThrow(() -> new NotFoundException("User not found"));
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException("Unauthenticated");
        }

        String username = jwt.getSubject();
        if (username == null || username.isBlank()) {
            throw new UnauthorizedException("Invalid token claims");
        }

        return userProfileRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public void updateFcmToken(UUID userId, String fcmToken) {
        UserProfile user = getUser(userId);
        user.setFcmToken(fcmToken);
        userProfileRepository.save(user);
    }

    private UUID resolveUserIdFromJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String userIdStr = jwt.getClaimAsString("userId");
            if (userIdStr != null && !userIdStr.isBlank()) {
                return UUID.fromString(userIdStr);
            }
        }
        return null;
    }

    private UUID resolveUserIdFromHeader() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }

        String userIdHeader = attributes.getRequest().getHeader("x-user-id");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid x-user-id header");
        }
    }
}
