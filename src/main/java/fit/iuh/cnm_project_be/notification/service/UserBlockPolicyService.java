package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.user.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserBlockPolicyService {

    private final UserBlockRepository userBlockRepository;

    @Transactional(readOnly = true)
    public boolean hasRecipientBlockedActor(UUID recipientId, UUID actorId) {
        if (recipientId == null || actorId == null) {
            return false;
        }
        return userBlockRepository.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(recipientId, actorId);
    }

    @Transactional(readOnly = true)
    public boolean hasActorBlockedRecipient(UUID actorId, UUID recipientId) {
        if (actorId == null || recipientId == null) {
            return false;
        }
        return userBlockRepository.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(actorId, recipientId);
    }

    @Transactional(readOnly = true)
    public boolean isBlockedBetween(UUID actorId, UUID recipientId) {
        return hasActorBlockedRecipient(actorId, recipientId) || hasRecipientBlockedActor(recipientId, actorId);
    }
}
