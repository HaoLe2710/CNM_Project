package fit.iuh.cnm_project_be.user.dto.response;

import fit.iuh.cnm_project_be.user.enums.FriendRequestStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendRealtimePayload {
    Long requestId;
    UUID actorUserId;
    UUID senderId;
    UUID receiverId;
    UUID otherUserId;
    FriendRequestStatus requestStatus;
    FriendRequestResponse request;
}
