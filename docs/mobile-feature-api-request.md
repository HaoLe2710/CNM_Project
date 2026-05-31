# Mobile Feature API Request

Tài liệu này tổng hợp các API BE hiện có để FE Mobile triển khai các chức năng chat, group, presence, notification, reminder/calendar, voice message, cloud storage, friend tags và group labels.

## 1. Base Contract

Base URL tùy môi trường:

```txt
{API_BASE_URL}
```

Một số API cần header user hiện tại:

```http
x-user-id: <UUID>
Authorization: Bearer <accessToken>
```

Response wrapper chung:

```ts
export type ApiResponse<T> = {
  success: boolean;
  code: string;
  message: string;
  data: T;
  errors: unknown;
  meta: {
    requestId: string;
    timestamp: string;
  };
};
```

## 2. Realtime WebSocket

STOMP/SockJS endpoint:

```txt
/ws
```

Topics FE cần subscribe:

```txt
/topic/conversations/{conversationId}
/topic/users/{userId}/conversations
/topic/users/{userId}/conversations/status
/topic/typing/{conversationId}
/topic/messages/{messageId}/status
/topic/presence
/topic/users/{userId}/notifications
/topic/users/{userId}/message-processing
```

Realtime event wrapper cho chat/conversation:

```ts
export type RealtimeEvent<T> = {
  type:
    | "MESSAGE_CREATED"
    | "MESSAGE_UPDATED"
    | "MESSAGE_DELETED"
    | "MESSAGE_REACTION_UPDATED"
    | "MESSAGE_STATUS_UPDATED"
    | "TYPING_UPDATED"
    | "CONVERSATION_UPDATED";
  payload: T;
};
```

Presence realtime payload:

```ts
export type PresenceRealtimePayload = {
  eventType: "PRESENCE_CHANGED";
  userId: string;
  online: boolean;
  lastSeenAt: string | null;
  occurredAt: string;
};
```

Notification realtime payload:

```ts
export type NotificationRealtimePayload = {
  eventType: string;
  notificationId: string;
  notification: NotificationResponse;
  unreadCount: number;
  occurredAt: string;
};
```

Message processing realtime payload:

```ts
export type MessageProcessingRealtimePayload = {
  eventType: "MESSAGE_PROCESSING_UPDATED";
  jobId: string;
  messageId: number;
  conversationId: string;
  attachmentId?: number;
  jobType: "STT" | "TTS";
  jobScope: string;
  status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
  resultText?: string;
  audioUrl?: string;
  errorMessage?: string;
  occurredAt: string;
};
```

## 3. Shared Types

```ts
export type UUID = string;

export type CursorPageResponse<T> = {
  items: T[];
  nextCursor?: string;
  hasMore: boolean;
};

export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasMore: boolean;
};
```

## 4. Chat, Messages, Group

### Types

```ts
export type MessageType =
  | "TEXT"
  | "IMAGE"
  | "VIDEO"
  | "FILE"
  | "AUDIO"
  | "CALL_LOG"
  | "SYSTEM";

export type ConversationNotificationLevel = "ALL" | "MENTIONS_ONLY" | "NONE";

export type ConversationMemberResponse = {
  userId: UUID;
  username?: string;
  displayName?: string;
  nickname?: string;
  avatarUrl?: string;
  role: "OWNER" | "ADMIN" | "MEMBER";
};

export type ConversationResponse = {
  id: UUID;
  name?: string;
  avatarUrl?: string;
  type: "PRIVATE" | "GROUP";
  lastMessage: string;
  lastMessageTime: string;
  unreadCount: number;
  muted: boolean;
  archived: boolean;
  pinned: boolean;
  notificationLevel: ConversationNotificationLevel;
  customName?: string;
  backgroundType?: string;
  backgroundColor?: string;
  backgroundImageUrl?: string;
  groupLabel?: string;
  groupLabelDisplayName?: string;
  groupLabelColor?: string;
  displayName?: string;
  peerUserId?: UUID;
  peerDisplayName?: string;
  peerAvatarUrl?: string;
  members: ConversationMemberResponse[];
};

export type MessageAttachmentPayload = {
  url: string;
  storageKey?: string;
  fileName: string;
  contentType?: string;
  fileSize: number;
  type: MessageType;
  durationMs?: number;
  waveform?: number[];
  audioFormat?: string;
};

export type MessageAttachmentResponse = MessageAttachmentPayload & {
  id: number;
};

export type ReplyInfo = {
  messageId: number;
  senderId?: UUID;
  senderDisplayName?: string;
  senderAvatarUrl?: string;
  contentPreview?: string;
  type?: MessageType;
};

export type MessageResponse = {
  id: number;
  conversationId: UUID;
  senderId: UUID;
  senderDisplayName?: string;
  senderAvatarUrl?: string;
  content?: string;
  originalLinkUrl?: string;
  type: MessageType;
  replyTo?: ReplyInfo;
  attachments: MessageAttachmentResponse[];
  reactions: { type: string; count: number }[];
  myReaction?: string;
  seen?: boolean;
  createdAt: string;
  editedAt?: string;
  pinnedAt?: string;
};

export type SendMessageRequest = {
  conversationId: UUID;
  content?: string;
  originalLinkUrl?: string;
  replyToMessageId?: number;
  messageType?: MessageType;
  attachments?: MessageAttachmentPayload[];
};
```

### Conversation APIs

```http
GET /api/v1/conversations?archived=false&groupLabel=WORK
```

Response: `ApiResponse<ConversationResponse[]>`

```http
GET /api/v1/conversations/group-labels
```

Response:

```ts
type ConversationGroupLabelPresetResponse = {
  code: "FRIENDS" | "WORK" | "STUDY" | "FAMILY" | "PROJECT" | "OTHER";
  displayName: string;
  color: string;
};
```

```http
POST /api/v1/conversations
Content-Type: application/json
```

Body phụ thuộc BE hiện tại của create conversation. FE dùng theo màn hình tạo chat/group.

```http
PATCH /api/v1/conversations/{conversationId}
```

Body:

```json
{ "name": "Tên nhóm mới" }
```

```http
PATCH /api/v1/conversations/{conversationId}/avatar
```

Body:

```json
{ "avatarUrl": "https://..." }
```

```http
POST /api/v1/conversations/{conversationId}/members
```

Body:

```json
{ "userId": "uuid" }
```

```http
DELETE /api/v1/conversations/{conversationId}/members/{memberUserId}
PATCH  /api/v1/conversations/{conversationId}/members/{memberUserId}/nickname
POST   /api/v1/conversations/{conversationId}/leave
```

Nickname body:

```json
{ "nickname": "Biệt danh" }
```

Group label:

```http
GET   /api/v1/conversations/{conversationId}/group-label
PATCH /api/v1/conversations/{conversationId}/group-label
```

Patch body:

```json
{ "groupLabel": "WORK" }
```

### Message APIs

```http
POST /api/v1/messages
```

Body:

```json
{
  "conversationId": "uuid",
  "content": "Hello",
  "messageType": "TEXT",
  "replyToMessageId": null,
  "attachments": []
}
```

Response: `ApiResponse<MessageResponse>`

```http
GET /api/v1/messages/{conversationId}?cursor=&size=50
GET /api/v1/messages/{conversationId}/context?messageId=123&range=50
PATCH /api/v1/messages/{messageId}
PATCH /api/v1/messages/{messageId}/pin
PATCH /api/v1/messages/{messageId}/status?status=SEEN
POST /api/v1/messages/typing/{conversationId}?isTyping=true
PATCH /api/v1/messages/mark-seen/{conversationId}
DELETE /api/v1/messages/{messageId}
PUT /api/v1/messages/{messageId}/reaction
DELETE /api/v1/messages/{messageId}/reaction
POST /api/v1/messages/{messageId}/hide
PATCH /api/v1/messages/{messageId}/remove-for-me
POST /api/v1/messages/attachments/upload
```

Pin body:

```json
{ "pinned": true }
```

Reaction body:

```json
{ "reactionType": "LIKE" }
```

Attachment upload multipart:

```txt
file=<binary>
```

Attachment response:

```ts
type UploadAttachmentResponse = {
  url: string;
  storageKey: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  type: MessageType;
};
```

### System Messages

BE trả system message với:

```ts
message.type === "SYSTEM"
```

Các event group hiện có:

```txt
group_member_added
group_member_removed
group_left
group_admin_promoted
group_admin_demoted
group_owner_transferred
group_renamed
group_avatar_changed
group_background_changed
group_nickname_changed
group_disbanded
```

FE có thể render `message.content` trực tiếp nếu BE đã resolve, hoặc parse payload dạng prefix `[[GROUP_SYSTEM]]{...}` nếu cần UI custom.

Lưu ý thiếu so với spec Mobile:

```txt
Chưa có system message riêng cho pin/unpin message.
Chưa có system message timeline cho create/update/delete reminder.
```

## 5. Online / Offline Presence

```http
GET /api/v1/presence/users/{userId}
```

```http
POST /api/v1/presence/users/batch
```

Body:

```json
{
  "userIds": ["uuid-1", "uuid-2"]
}
```

```http
GET /api/v1/presence/conversations/{conversationId}
```

Types:

```ts
export type PresenceItemResponse = {
  userId: UUID;
  online: boolean;
  lastSeenAt?: string;
};

export type BatchPresenceResponse = {
  items: PresenceItemResponse[];
};

export type ConversationPresenceResponse = {
  conversationId: UUID;
  items: PresenceItemResponse[];
};
```

FE dùng:

- Chat list: batch user IDs từ private chats.
- Chat header: `GET /presence/conversations/{conversationId}`.
- Realtime: subscribe `/topic/presence`.

## 6. Notifications / Push Notifications

### Notification APIs

```http
GET /api/v1/notifications?cursor=&limit=20&unreadOnly=false
GET /api/v1/notifications/unread-count
PATCH /api/v1/notifications/{notificationId}/read
PATCH /api/v1/notifications/read-all
DELETE /api/v1/notifications/{notificationId}
```

Types:

```ts
export type NotificationResponse = {
  id: UUID;
  recipientId: UUID;
  actorId?: UUID;
  type:
    | "NEW_PRIVATE_MESSAGE"
    | "NEW_GROUP_MESSAGE"
    | "GROUP_MENTION"
    | "REPLY_TO_MY_MESSAGE"
    | "REACTION_TO_MY_MESSAGE"
    | "INCOMING_PRIVATE_CALL"
    | "MISSED_PRIVATE_CALL"
    | "GROUP_CALL_STARTED"
    | "MISSED_GROUP_CALL"
    | "GROUP_RENAMED"
    | "GROUP_AVATAR_CHANGED"
    | "GROUP_BACKGROUND_CHANGED"
    | "GROUP_MEMBER_ADDED"
    | "GROUP_MEMBER_REMOVED"
    | "GROUP_NICKNAME_CHANGED"
    | "GROUP_ADMIN_PROMOTED"
    | "GROUP_ADMIN_DEMOTED"
    | "GROUP_OWNER_TRANSFERRED"
    | "GROUP_LEFT"
    | "GROUP_DISBANDED"
    | "FRIEND_REQUEST_RECEIVED"
    | "FRIEND_REQUEST_ACCEPTED"
    | "POST_REACTION"
    | "POST_COMMENT"
    | "COMMENT_REPLY"
    | "COMMENT_MENTION"
    | "POST_TAGGED"
    | "POST_SHARED"
    | "REMINDER_CREATED"
    | "REMINDER_DUE"
    | "REMINDER_UPDATED"
    | "REMINDER_CANCELLED"
    | "SYSTEM_NOTICE";
  title: string;
  body: string;
  targetType?: string;
  targetId?: UUID;
  conversationId?: UUID;
  messageId?: number;
  postId?: UUID;
  commentId?: UUID;
  metadata?: Record<string, unknown>;
  readAt?: string;
  createdAt: string;
  expiresAt?: string;
  unread: boolean;
};

export type NotificationPageResponse = {
  items: NotificationResponse[];
  nextCursor?: string;
  hasMore: boolean;
};
```

### Device Token APIs

```http
POST /api/v1/device-tokens
GET /api/v1/device-tokens/me
DELETE /api/v1/device-tokens/{deviceId}?platform=EXPO
```

Register Expo token:

```json
{
  "deviceId": "expo-device-id-or-installation-id",
  "platform": "EXPO",
  "provider": "FCM",
  "token": "ExponentPushToken[...]"
}
```

### Conversation Notification Setting

```http
GET /api/v1/conversations/{conversationId}/notification-settings
PATCH /api/v1/conversations/{conversationId}/notification-settings
```

Body:

```json
{
  "notificationLevel": "ALL",
  "mutedUntil": null
}
```

Response:

```ts
export type ConversationNotificationSettingResponse = {
  conversationId: UUID;
  userId: UUID;
  notificationLevel: ConversationNotificationLevel;
  mutedUntil?: string;
  muted: boolean;
  lastMutedAt?: string;
  updatedAt?: string;
};
```

## 7. Reminder / Calendar

### APIs

```http
POST /api/v1/conversations/{conversationId}/reminders
GET /api/v1/conversations/{conversationId}/reminders?status=&from=&to=&page=&size=
GET /api/v1/reminders?status=&scope=TODAY&from=&to=&page=&size=
PATCH /api/v1/reminders/{reminderId}
DELETE /api/v1/reminders/{reminderId}
POST /api/v1/reminders/{reminderId}/cancel
POST /api/v1/reminders/{reminderId}/complete
POST /api/v1/reminders/{reminderId}/ack
POST /api/v1/reminders/{reminderId}/dismiss
```

Create body:

```ts
export type CreateConversationReminderRequest = {
  title: string;
  description?: string;
  remindAt: string;
  timezone?: string;
  participantIds?: UUID[];
};
```

Update body:

```ts
export type UpdateConversationReminderRequest = Partial<CreateConversationReminderRequest>;
```

Response:

```ts
export type ReminderStatus = "SCHEDULED" | "DUE" | "COMPLETED" | "CANCELLED";
export type ReminderScope = "TODAY" | "WEEK" | "UPCOMING" | "PAST";
export type ReminderParticipantStatus = "PENDING" | "ACKNOWLEDGED" | "DISMISSED" | "DONE";

export type ReminderParticipantResponse = {
  userId: UUID;
  displayName?: string;
  avatarUrl?: string;
  status: ReminderParticipantStatus;
  readAt?: string;
  acknowledgedAt?: string;
  dismissedAt?: string;
};

export type ConversationReminderResponse = {
  id: UUID;
  conversationId: UUID;
  conversationName?: string;
  createdBy: UUID;
  createdByName?: string;
  title: string;
  description?: string;
  remindAt: string;
  timezone?: string;
  status: ReminderStatus;
  participants: ReminderParticipantResponse[];
  dueNotifiedAt?: string;
  completedAt?: string;
  cancelledAt?: string;
  createdAt: string;
  updatedAt: string;
};

export type ReminderPageResponse = PageResponse<ConversationReminderResponse>;
```

Calendar screen dùng:

```http
GET /api/v1/reminders?scope=UPCOMING&page=0&size=50
```

Mock display text FE cần format đúng:

```txt
Nhắc hẹn: Kiểm tra tủ đông, kem tủ mát – Thứ ba, 19 Tháng 5 lúc 20:00
Nhắc hẹn: Kiểm tra tủ đông, mát – Thứ tư, 20 Tháng 5 lúc 8:00
Nhắc hẹn: Kiểm tra tủ đông, kem tủ mát – Thứ Năm, 7 tháng 5 lúc 20:00
```

## 8. Voice Messages, STT, TTS

### Voice Message

Gửi voice message:

1. Upload audio qua `POST /api/v1/messages/attachments/upload`.
2. Gửi message với `messageType: "AUDIO"` và attachment có `durationMs`, `waveform`, `audioFormat`.

Example:

```json
{
  "conversationId": "uuid",
  "messageType": "AUDIO",
  "attachments": [
    {
      "url": "https://...",
      "storageKey": "media/...",
      "fileName": "voice.m4a",
      "contentType": "audio/m4a",
      "fileSize": 123456,
      "type": "AUDIO",
      "durationMs": 12000,
      "waveform": [0.1, 0.4, 0.2],
      "audioFormat": "m4a"
    }
  ]
}
```

### Speech-To-Text

```http
POST /api/v1/messages/{messageId}/processing/stt
```

Body:

```json
{
  "attachmentId": 123,
  "language": "vi",
  "forceRefresh": false
}
```

### Text-To-Speech

```http
POST /api/v1/messages/{messageId}/processing/tts
```

Body:

```json
{
  "language": "vi",
  "voice": "alloy",
  "forceRefresh": false
}
```

### Dictation STT

```http
POST /api/v1/message-processing/dictation/stt
Content-Type: multipart/form-data
```

Form data:

```txt
audio=<binary>
conversationId=<UUID>
language=vi
audioFormat=m4a
durationMs=12000
```

Job APIs:

```http
GET /api/v1/messages/{messageId}/processing/latest?jobType=STT&attachmentId=123
GET /api/v1/message-processing/jobs/{jobId}
POST /api/v1/message-processing/jobs/{jobId}/retry
```

Job response:

```ts
export type MessageProcessingJobResponse = {
  id: UUID;
  messageId: number;
  conversationId: UUID;
  attachmentId?: number;
  jobType: "STT" | "TTS";
  jobScope: string;
  status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
  provider?: string;
  resultText?: string;
  resultFileUrl?: string;
  resultStorageKey?: string;
  resultMimeType?: string;
  errorMessage?: string;
  retryCount: number;
  createdAt: string;
  updatedAt: string;
  startedAt?: string;
  completedAt?: string;
  nextAttemptAt?: string;
};
```

## 9. Personal Cloud Storage / My Documents

### APIs

```http
GET /api/v1/cloud/files?parentFolderId=&q=&type=&page=&size=
GET /api/v1/cloud/trash?q=&type=&page=&size=
POST /api/v1/cloud/files/upload
POST /api/v1/cloud/folders
POST /api/v1/cloud/links
POST /api/v1/cloud/manual-items
PATCH /api/v1/cloud/files/{fileId}
DELETE /api/v1/cloud/files/{fileId}
POST /api/v1/cloud/files/{fileId}/restore
DELETE /api/v1/cloud/files/{fileId}/permanent
POST /api/v1/cloud/files/{fileId}/send-to-conversation
GET /api/v1/cloud/storage/summary
```

Cloud file types:

```ts
export type CloudFileType =
  | "FOLDER"
  | "IMAGE"
  | "VIDEO"
  | "AUDIO"
  | "DOCUMENT"
  | "ARCHIVE"
  | "LINK"
  | "MANUAL"
  | "OTHER";
```

Response:

```ts
export type CloudFileResponse = {
  id: UUID;
  parentFolderId?: UUID;
  name: string;
  originalFileName?: string;
  mimeType?: string;
  fileExtension?: string;
  fileSize?: number;
  fileType: CloudFileType;
  fileUrl?: string;
  storageKey?: string;
  manualContent?: string;
  isFolder: boolean;
  deletedAt?: string;
  createdAt: string;
  updatedAt: string;
};

export type CloudFilePageResponse = PageResponse<CloudFileResponse>;

export type CloudStorageSummaryResponse = {
  totalBytes: number;
  activeBytes: number;
  trashBytes: number;
  usedBytes: number;
  quotaBytes: number;
  remainingBytes: number;
  usagePercent: number;
  totalFiles: number;
  totalFolders: number;
  byType: Record<string, number>;
};
```

Upload multipart:

```txt
file=<binary>
parentFolderId=<optional UUID>
```

Create folder:

```json
{
  "name": "Tên thư mục",
  "parentFolderId": null
}
```

Create link:

```json
{
  "name": "Link biểu mẫu",
  "url": "https://forms.office.com/r/...",
  "parentFolderId": null
}
```

Create manual item:

```json
{
  "name": "Ghi chú thủ công",
  "content": "Nội dung nhập tay",
  "parentFolderId": null
}
```

Rename:

```json
{ "name": "Tên mới" }
```

Send cloud file to conversation:

```json
{
  "conversationId": "uuid",
  "message": "Gửi bạn file này"
}
```

UI tab mapping:

```txt
Tất cả    -> không truyền type
Ảnh       -> type=IMAGE
File      -> FE có thể gom DOCUMENT, ARCHIVE, OTHER hoặc gọi all rồi filter
Link      -> type=LINK
Văn bản   -> type=DOCUMENT
Thủ công  -> type=MANUAL
```

## 10. Friend Tags / Close Friends

```http
GET /api/v1/friends?closeOnly=false
GET /api/v1/friends?closeOnly=true
GET /api/v1/friends/close
GET /api/v1/friends/settings
GET /api/v1/friends/{friendId}/settings
PATCH /api/v1/friends/{friendId}/settings
```

Patch body:

```json
{
  "isCloseFriend": true,
  "note": "Bạn thân"
}
```

Types:

```ts
export type UserSummaryResponse = {
  userId: UUID;
  username?: string;
  displayName?: string;
  avatarUrl?: string;
};

export type FriendshipResponse = {
  friendshipId: UUID;
  createdAt: string;
  isCloseFriend: boolean;
  closeFriendNote?: string;
  friend: UserSummaryResponse;
};

export type FriendshipSettingResponse = {
  userId: UUID;
  friendId: UUID;
  isCloseFriend: boolean;
  note?: string;
  createdAt: string;
  updatedAt: string;
};
```

FE Contacts screen:

- All contacts: `GET /api/v1/friends`
- Bạn thân filter: `GET /api/v1/friends?closeOnly=true` hoặc `GET /api/v1/friends/close`
- Update tag: `PATCH /api/v1/friends/{friendId}/settings`

## 11. Social Feed / Multi-Image Posts / Stories

### Post Feed APIs

```http
GET /api/v1/social/posts/feed?size=20
GET /api/v1/social/posts/me?archived=false&size=20
POST /api/v1/social/posts
POST /api/v1/social/posts/upload
PATCH /api/v1/social/posts/{postId}/archive
PATCH /api/v1/social/posts/{postId}/restore
DELETE /api/v1/social/posts/{postId}
PUT /api/v1/social/posts/{postId}/like
DELETE /api/v1/social/posts/{postId}/like
GET /api/v1/social/posts/{postId}/comments
POST /api/v1/social/posts/{postId}/comments
GET /api/v1/social/posts/{postId}/interactions
GET /api/v1/social/posts/{postId}/audience
```

Post media type:

```ts
export type SocialMediaType = "IMAGE" | "VIDEO";
```

Create post bằng JSON sau khi upload media:

```http
POST /api/v1/social/posts
Content-Type: application/json
```

```json
{
  "caption": "Album cuối tuần",
  "visibilityMode": "ALL_FRIENDS",
  "mediaItems": [
    {
      "mediaUrl": "https://cdn.example.com/image-1.jpg",
      "mediaType": "IMAGE"
    },
    {
      "mediaUrl": "https://cdn.example.com/image-2.jpg",
      "mediaType": "IMAGE"
    }
  ],
  "allowedViewerIds": [],
  "taggedFriendIds": []
}
```

Create post bằng multipart, dùng cho chọn nhiều ảnh/video trực tiếp:

```http
POST /api/v1/social/posts/upload
Content-Type: multipart/form-data
```

FE Mobile có thể gửi nhiều file bằng một trong các field name sau:

```txt
files=<file1>
files=<file2>
files[]=<file1>
files[]=<file2>
file=<file1>
file=<file2>
mediaFiles=<file1>
mediaFiles=<file2>
mediaFiles[]=<file1>
mediaFiles[]=<file2>
```

Form fields:

```txt
caption=Album cuối tuần
visibilityMode=ALL_FRIENDS
allowedViewerIds=<uuid>   // optional, repeatable
taggedFriendIds=<uuid>    // optional, repeatable
```

Post visibility:

```txt
ALL_FRIENDS       -> tất cả bạn bè được xem
SELECTED_FRIENDS  -> chỉ allowedViewerIds được xem
```

Giới hạn BE:

```txt
Tối đa 10 media items/post.
Chỉ hỗ trợ IMAGE và VIDEO.
Response trả mediaItems đã sắp xếp theo sortOrder.
```

Response:

```ts
export type PostMediaResponse = {
  id: UUID;
  mediaUrl: string;
  mediaType: SocialMediaType;
  sortOrder: number;
};

export type PostResponse = {
  id: UUID;
  author: UserSummaryResponse;
  imageUrl: string;
  mediaItems: PostMediaResponse[];
  caption?: string;
  archived: boolean;
  archivedAt?: string;
  visibilityMode: "ALL_FRIENDS" | "SELECTED_FRIENDS";
  taggedFriends: UserSummaryResponse[];
  likeCount: number;
  commentCount: number;
  likedByCurrentUser: boolean;
  interactionScope: string;
  createdAt: string;
  updatedAt?: string;
};
```

### Media Upload API

Nếu FE muốn upload trước rồi tự gọi JSON:

```http
POST /api/v1/social/media/upload
Content-Type: multipart/form-data
```

Form:

```txt
file=<image-or-video>
```

Response:

```ts
export type SocialMediaUploadResponse = {
  url: string;
  storageKey: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  mediaType: "IMAGE" | "VIDEO";
};
```

### Story / Moment APIs

```http
POST /api/v1/social/moments
POST /api/v1/social/moments/upload
GET /api/v1/social/moments/me?size=20
GET /api/v1/social/moments/feed?size=20
DELETE /api/v1/social/moments/{momentId}
GET /api/v1/social/videos/feed?size=20
GET /api/v1/social/videos/community-feed?cursor=&size=20
PUT /api/v1/social/videos/{momentId}/like
DELETE /api/v1/social/videos/{momentId}/like
GET /api/v1/social/videos/{momentId}/comments
POST /api/v1/social/videos/{momentId}/comments
POST /api/v1/social/videos/{momentId}/view
```

Create story bằng JSON sau khi upload media:

```http
POST /api/v1/social/moments
Content-Type: application/json
```

```json
{
  "mediaUrl": "https://cdn.example.com/story.jpg",
  "mediaType": "IMAGE",
  "caption": "Story hôm nay",
  "coverUrl": null,
  "durationSeconds": 0,
  "visibilityMode": "FRIENDS",
  "audioMode": "NONE",
  "musicTrackId": null,
  "musicTitle": null,
  "musicArtist": null,
  "musicUrl": null,
  "musicStartSeconds": 0
}
```

Create story bằng multipart trực tiếp:

```http
POST /api/v1/social/moments/upload
Content-Type: multipart/form-data
```

Form:

```txt
file=<image-or-video>
caption=Story hôm nay
coverUrl=<optional-url>
durationSeconds=0
visibilityMode=FRIENDS
audioMode=NONE
musicTrackId=<optional>
musicTitle=<optional>
musicArtist=<optional>
musicUrl=<optional>
musicStartSeconds=0
```

Story rules:

```txt
IMAGE: audioMode chỉ được NONE hoặc REPLACED.
VIDEO: audioMode mặc định ORIGINAL; không dùng NONE.
REPLACED: bắt buộc có musicUrl.
```

Response:

```ts
export type MomentResponse = {
  id: UUID;
  author: UserSummaryResponse;
  mediaUrl: string;
  coverUrl?: string;
  mediaType: "IMAGE" | "VIDEO";
  caption?: string;
  durationSeconds: number;
  visibilityMode: "PUBLIC" | "FRIENDS" | "PRIVATE";
  audioMode: "NONE" | "ORIGINAL" | "MUTED" | "REPLACED";
  musicTrackId?: string;
  musicTitle?: string;
  musicArtist?: string;
  musicUrl?: string;
  musicStartSeconds: number;
  likeCount: number;
  commentCount: number;
  shareCount: number;
  viewCount: number;
  likedByCurrentUser: boolean;
  followedByCurrentUser: boolean;
  createdAt: string;
};
```

Fix BE đã áp dụng:

```txt
DB migration V47 đổi moments.media_type từ constraint lowercase image/video sang enum uppercase IMAGE/VIDEO.
POST /social/posts/upload nhận nhiều files với nhiều field name phổ biến.
Thêm POST /social/moments/upload để FE đăng story trực tiếp bằng multipart.
```

## 12. System Message Timeline

Các event sau hiện đã tạo thêm `MessageType.SYSTEM` trong chat timeline. FE chỉ cần render `message.type === "SYSTEM"` và hiển thị `message.content`.

Pin/unpin:

```http
PATCH /api/v1/messages/{messageId}/pin
```

Body:

```json
{ "pinned": true }
```

Timeline examples:

```txt
Trúc Ly đã ghim 1 tin nhắn https://forms.office.com/r/...
Trúc Ly bỏ ghim 1 tin nhắn https://forms.office.com/r/...
```

Reminder lifecycle:

```http
POST   /api/v1/conversations/{conversationId}/reminders
PATCH  /api/v1/reminders/{reminderId}
DELETE /api/v1/reminders/{reminderId}
POST   /api/v1/reminders/{reminderId}/cancel
```

Timeline examples:

```txt
Trúc Ly tạo nhắc hẹn Kiểm tra tủ đông, mát
Trúc Ly cập nhật nhắc hẹn Kiểm tra tủ đông, mát
Trúc Ly xóa nhắc hẹn Kiểm tra tủ đông, mát
Trúc Ly hủy nhắc hẹn Kiểm tra tủ đông, mát
```

## 13. Remaining Notes

1. Exact Vietnamese display names bị lỗi encoding ở một số enum/group label trong source.
   - FE có thể tự map code sang text chuẩn:
     - `FRIENDS`: `Bạn bè`
     - `WORK`: `Công việc`
     - `STUDY`: `Học tập`
     - `FAMILY`: `Gia đình`
     - `PROJECT`: `Dự án`
     - `OTHER`: `Khác`
