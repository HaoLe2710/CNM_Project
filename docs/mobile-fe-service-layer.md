# Mobile FE API Service Layer

Tài liệu này dành cho FE Mobile Expo/React Native. Copy các block code vào app Mobile theo cấu trúc:

```txt
src/services/apiClient.ts
src/services/chatService.ts
src/services/reminderService.ts
src/services/cloudService.ts
src/services/taggingService.ts
```

Không bao gồm IoT/Image disease analysis.

## 1. `src/services/apiClient.ts`

```ts
import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from "axios";

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

export type ApiError = {
  code?: string;
  message: string;
  status?: number;
  errors?: unknown;
};

export type AuthTokenProvider = {
  getAccessToken: () => Promise<string | null> | string | null;
  getCurrentUserId?: () => Promise<string | null> | string | null;
  onUnauthorized?: () => void;
};

let authProvider: AuthTokenProvider | null = null;

export const setAuthProvider = (provider: AuthTokenProvider) => {
  authProvider = provider;
};

export const apiClient: AxiosInstance = axios.create({
  baseURL: process.env.EXPO_PUBLIC_API_BASE_URL,
  timeout: 30000,
  headers: {
    Accept: "application/json",
  },
});

apiClient.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const token = await authProvider?.getAccessToken?.();
  const userId = await authProvider?.getCurrentUserId?.();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  if (userId) {
    config.headers["x-user-id"] = userId;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    if (error.response?.status === 401) {
      authProvider?.onUnauthorized?.();
    }

    const apiError: ApiError = {
      status: error.response?.status,
      code: error.response?.data?.code,
      message: error.response?.data?.message ?? error.message ?? "Network error",
      errors: error.response?.data?.errors,
    };
    return Promise.reject(apiError);
  },
);

export const unwrap = <T>(response: { data: ApiResponse<T> }): T => response.data.data;
```

Usage khi app login xong:

```ts
setAuthProvider({
  getAccessToken: () => authStore.accessToken,
  getCurrentUserId: () => authStore.userId,
  onUnauthorized: () => authStore.logout(),
});
```

## 2. `src/services/chatService.ts`

```ts
import { apiClient, unwrap } from "./apiClient";

export type UUID = string;

export type MessageType =
  | "TEXT"
  | "IMAGE"
  | "VIDEO"
  | "FILE"
  | "AUDIO"
  | "CALL_LOG"
  | "SYSTEM";

export type ConversationNotificationLevel = "ALL" | "MENTIONS_ONLY" | "NONE";
export type MessageDeliveryStatus = "SENT" | "DELIVERED" | "SEEN";
export type MessageProcessingJobType = "STT" | "TTS";
export type MessageProcessingStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";

export type CursorPageResponse<T> = {
  items: T[];
  nextCursor?: string;
  hasMore: boolean;
};

export type PresenceItemResponse = {
  userId: UUID;
  online: boolean;
  lastSeenAt?: string | null;
};

export type BatchPresenceResponse = {
  items: PresenceItemResponse[];
};

export type ConversationPresenceResponse = {
  conversationId: UUID;
  items: PresenceItemResponse[];
};

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

export type UploadAttachmentResponse = {
  url: string;
  storageKey: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  type: MessageType;
};

export type MessageProcessingJobResponse = {
  id: UUID;
  messageId?: number;
  conversationId: UUID;
  attachmentId?: number;
  jobType: MessageProcessingJobType;
  jobScope: string;
  status: MessageProcessingStatus;
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

export const isSystemMessage = (message: MessageResponse) => message.type === "SYSTEM";

export const chatService = {
  getConversations: (params?: { archived?: boolean; groupLabel?: string }) =>
    apiClient.get("/api/v1/conversations", { params }).then(unwrap<ConversationResponse[]>),

  getMessages: (conversationId: UUID, params?: { cursor?: string; size?: number }) =>
    apiClient
      .get(`/api/v1/messages/${conversationId}`, { params })
      .then(unwrap<CursorPageResponse<MessageResponse>>),

  getMessageContext: (conversationId: UUID, params: { messageId: number; range?: number }) =>
    apiClient
      .get(`/api/v1/messages/${conversationId}/context`, { params })
      .then(unwrap),

  sendMessage: (body: SendMessageRequest) =>
    apiClient.post("/api/v1/messages", body).then(unwrap<MessageResponse>),

  editMessage: (messageId: number, body: { content?: string; originalLinkUrl?: string }) =>
    apiClient.patch(`/api/v1/messages/${messageId}`, body).then(unwrap<MessageResponse>),

  setPinned: (messageId: number, pinned: boolean) =>
    apiClient.patch(`/api/v1/messages/${messageId}/pin`, { pinned }).then(unwrap<MessageResponse>),

  updateStatus: (messageId: number, status: MessageDeliveryStatus) =>
    apiClient.patch(`/api/v1/messages/${messageId}/status`, null, { params: { status } }).then(unwrap<void>),

  sendTyping: (conversationId: UUID, isTyping: boolean) =>
    apiClient.post(`/api/v1/messages/typing/${conversationId}`, null, { params: { isTyping } }).then(unwrap<void>),

  markConversationSeen: (conversationId: UUID) =>
    apiClient.patch(`/api/v1/messages/mark-seen/${conversationId}`).then(unwrap<void>),

  deleteMessage: (messageId: number) =>
    apiClient.delete(`/api/v1/messages/${messageId}`).then(unwrap<void>),

  removeForMe: (messageId: number) =>
    apiClient.patch(`/api/v1/messages/${messageId}/remove-for-me`).then(unwrap<void>),

  hideMessage: (messageId: number) =>
    apiClient.post(`/api/v1/messages/${messageId}/hide`).then(unwrap<void>),

  uploadAttachment: (file: { uri: string; name: string; type: string }) => {
    const formData = new FormData();
    formData.append("file", file as unknown as Blob);
    return apiClient
      .post("/api/v1/messages/attachments/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      })
      .then(unwrap<UploadAttachmentResponse>);
  },

  getUserPresence: (userId: UUID) =>
    apiClient.get(`/api/v1/presence/users/${userId}`).then(unwrap<PresenceItemResponse>),

  getBatchPresence: (userIds: UUID[]) =>
    apiClient.post("/api/v1/presence/users/batch", { userIds }).then(unwrap<BatchPresenceResponse>),

  getConversationPresence: (conversationId: UUID) =>
    apiClient.get(`/api/v1/presence/conversations/${conversationId}`).then(unwrap<ConversationPresenceResponse>),

  requestSpeechToText: (
    messageId: number,
    body?: { attachmentId?: number; language?: string; forceRefresh?: boolean },
  ) =>
    apiClient
      .post(`/api/v1/messages/${messageId}/processing/stt`, body ?? {})
      .then(unwrap<MessageProcessingJobResponse>),

  requestTextToSpeech: (
    messageId: number,
    body?: { language?: string; voice?: string; forceRefresh?: boolean },
  ) =>
    apiClient
      .post(`/api/v1/messages/${messageId}/processing/tts`, body ?? {})
      .then(unwrap<MessageProcessingJobResponse>),

  requestDictationSpeechToText: (
    audio: { uri: string; name: string; type: string },
    params: { conversationId: UUID; language?: string; audioFormat?: string; durationMs?: number },
  ) => {
    const formData = new FormData();
    formData.append("audio", audio as unknown as Blob);
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null) formData.append(key, String(value));
    });
    return apiClient
      .post("/api/v1/message-processing/dictation/stt", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      })
      .then(unwrap<MessageProcessingJobResponse>);
  },

  getLatestProcessingJob: (messageId: number, params: { jobType: MessageProcessingJobType; attachmentId?: number }) =>
    apiClient
      .get(`/api/v1/messages/${messageId}/processing/latest`, { params })
      .then(unwrap<MessageProcessingJobResponse>),

  getProcessingJob: (jobId: UUID) =>
    apiClient.get(`/api/v1/message-processing/jobs/${jobId}`).then(unwrap<MessageProcessingJobResponse>),

  retryProcessingJob: (jobId: UUID) =>
    apiClient.post(`/api/v1/message-processing/jobs/${jobId}/retry`).then(unwrap<MessageProcessingJobResponse>),
};
```

Realtime FE cần subscribe:

```txt
/topic/conversations/{conversationId}
/topic/users/{userId}/conversations
/topic/typing/{conversationId}
/topic/presence
/topic/users/{userId}/message-processing
```

## 3. `src/services/reminderService.ts`

```ts
import { apiClient, unwrap } from "./apiClient";
import type { UUID } from "./chatService";

export type ReminderStatus = "SCHEDULED" | "DUE" | "COMPLETED" | "CANCELLED";
export type ReminderScope = "TODAY" | "WEEK" | "UPCOMING" | "PAST";
export type ReminderParticipantStatus = "PENDING" | "ACKNOWLEDGED" | "DISMISSED" | "DONE";

export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasMore: boolean;
};

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

export type CreateConversationReminderRequest = {
  title: string;
  description?: string;
  remindAt: string;
  timezone?: string;
  participantIds?: UUID[];
};

export type UpdateConversationReminderRequest = Partial<CreateConversationReminderRequest>;

export const reminderService = {
  getMyReminders: (params?: {
    status?: ReminderStatus;
    scope?: ReminderScope;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }) => apiClient.get("/api/v1/reminders", { params }).then(unwrap<ReminderPageResponse>),

  getCalendarItems: (params?: { from?: string; to?: string; page?: number; size?: number }) =>
    apiClient
      .get("/api/v1/reminders", {
        params: { scope: "UPCOMING", page: 0, size: 100, ...params },
      })
      .then(unwrap<ReminderPageResponse>),

  getConversationReminders: (
    conversationId: UUID,
    params?: { status?: ReminderStatus; from?: string; to?: string; page?: number; size?: number },
  ) =>
    apiClient
      .get(`/api/v1/conversations/${conversationId}/reminders`, { params })
      .then(unwrap<ReminderPageResponse>),

  createReminder: (conversationId: UUID, body: CreateConversationReminderRequest) =>
    apiClient
      .post(`/api/v1/conversations/${conversationId}/reminders`, body)
      .then(unwrap<ConversationReminderResponse>),

  updateReminder: (reminderId: UUID, body: UpdateConversationReminderRequest) =>
    apiClient.patch(`/api/v1/reminders/${reminderId}`, body).then(unwrap<ConversationReminderResponse>),

  deleteReminder: (reminderId: UUID) =>
    apiClient.delete(`/api/v1/reminders/${reminderId}`).then(unwrap<ConversationReminderResponse>),

  cancelReminder: (reminderId: UUID) =>
    apiClient.post(`/api/v1/reminders/${reminderId}/cancel`).then(unwrap<ConversationReminderResponse>),

  completeReminder: (reminderId: UUID) =>
    apiClient.post(`/api/v1/reminders/${reminderId}/complete`).then(unwrap<ConversationReminderResponse>),

  acknowledgeReminder: (reminderId: UUID) =>
    apiClient.post(`/api/v1/reminders/${reminderId}/ack`).then(unwrap<ConversationReminderResponse>),

  dismissReminder: (reminderId: UUID) =>
    apiClient.post(`/api/v1/reminders/${reminderId}/dismiss`).then(unwrap<ConversationReminderResponse>),
};
```

FE tự format title hiển thị:

```ts
export const formatReminderText = (title: string, formattedDate: string) =>
  `Nhắc hẹn: ${title} – ${formattedDate}`;
```

## 4. `src/services/cloudService.ts`

```ts
import { apiClient, unwrap } from "./apiClient";
import type { MessageResponse, UUID } from "./chatService";
import type { PageResponse } from "./reminderService";

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

export type CloudTab = "ALL" | "IMAGES" | "FILES" | "LINKS" | "TEXTS" | "MANUAL";

export const CLOUD_TAB_TO_TYPE: Record<Exclude<CloudTab, "ALL" | "FILES">, CloudFileType> = {
  IMAGES: "IMAGE",
  LINKS: "LINK",
  TEXTS: "DOCUMENT",
  MANUAL: "MANUAL",
};

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

export type CreateCloudLinkRequest = {
  name: string;
  url: string;
  parentFolderId?: UUID | null;
};

export type CreateCloudManualItemRequest = {
  name: string;
  content?: string;
  parentFolderId?: UUID | null;
};

export const cloudService = {
  listFiles: (params?: {
    parentFolderId?: UUID;
    q?: string;
    type?: CloudFileType;
    page?: number;
    size?: number;
  }) => apiClient.get("/api/v1/cloud/files", { params }).then(unwrap<CloudFilePageResponse>),

  listByTab: (tab: CloudTab, params?: { parentFolderId?: UUID; q?: string; page?: number; size?: number }) => {
    if (tab === "ALL") return cloudService.listFiles(params);
    if (tab === "FILES") return cloudService.listFiles(params); // FE lọc DOCUMENT/ARCHIVE/OTHER nếu cần.
    return cloudService.listFiles({ ...params, type: CLOUD_TAB_TO_TYPE[tab] });
  },

  listTrash: (params?: { q?: string; type?: CloudFileType; page?: number; size?: number }) =>
    apiClient.get("/api/v1/cloud/trash", { params }).then(unwrap<CloudFilePageResponse>),

  uploadFile: (file: { uri: string; name: string; type: string }, parentFolderId?: UUID) => {
    const formData = new FormData();
    formData.append("file", file as unknown as Blob);
    if (parentFolderId) formData.append("parentFolderId", parentFolderId);

    return apiClient
      .post("/api/v1/cloud/files/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      })
      .then(unwrap<CloudFileResponse>);
  },

  createFolder: (body: { name: string; parentFolderId?: UUID | null }) =>
    apiClient.post("/api/v1/cloud/folders", body).then(unwrap<CloudFileResponse>),

  createLink: (body: CreateCloudLinkRequest) =>
    apiClient.post("/api/v1/cloud/links", body).then(unwrap<CloudFileResponse>),

  createManualItem: (body: CreateCloudManualItemRequest) =>
    apiClient.post("/api/v1/cloud/manual-items", body).then(unwrap<CloudFileResponse>),

  renameFile: (fileId: UUID, name: string) =>
    apiClient.patch(`/api/v1/cloud/files/${fileId}`, { name }).then(unwrap<CloudFileResponse>),

  deleteFile: (fileId: UUID) =>
    apiClient.delete(`/api/v1/cloud/files/${fileId}`).then(unwrap<void>),

  restoreFile: (fileId: UUID) =>
    apiClient.post(`/api/v1/cloud/files/${fileId}/restore`).then(unwrap<CloudFileResponse>),

  permanentlyDeleteFile: (fileId: UUID) =>
    apiClient.delete(`/api/v1/cloud/files/${fileId}/permanent`).then(unwrap<void>),

  sendToConversation: (fileId: UUID, body: { conversationId: UUID; message?: string }) =>
    apiClient
      .post(`/api/v1/cloud/files/${fileId}/send-to-conversation`, body)
      .then(unwrap<MessageResponse>),

  getStorageSummary: () =>
    apiClient.get("/api/v1/cloud/storage/summary").then(unwrap<CloudStorageSummaryResponse>),
};
```

## 5. `src/services/taggingService.ts`

```ts
import { apiClient, unwrap } from "./apiClient";
import type { ConversationResponse, UUID } from "./chatService";

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

export type GroupLabelCode = "FRIENDS" | "WORK" | "STUDY" | "FAMILY" | "PROJECT" | "OTHER";

export const GROUP_LABEL_DISPLAY: Record<GroupLabelCode, string> = {
  WORK: "Công việc",
  FAMILY: "Gia đình",
  FRIENDS: "Bạn bè",
  STUDY: "Học tập",
  PROJECT: "Dự án",
  OTHER: "v.v.",
};

export type ConversationGroupLabelPresetResponse = {
  code: GroupLabelCode;
  displayName: string;
  color: string;
};

export type ConversationGroupLabelResponse = {
  conversationId: UUID;
  groupLabel?: GroupLabelCode;
  displayName?: string;
  color?: string;
};

export const taggingService = {
  getFriends: (closeOnly = false) =>
    apiClient.get("/api/v1/friends", { params: { closeOnly } }).then(unwrap<FriendshipResponse[]>),

  getCloseFriends: () =>
    apiClient.get("/api/v1/friends/close").then(unwrap<FriendshipResponse[]>),

  getFriendshipSettings: () =>
    apiClient.get("/api/v1/friends/settings").then(unwrap<FriendshipSettingResponse[]>),

  getFriendshipSetting: (friendId: UUID) =>
    apiClient.get(`/api/v1/friends/${friendId}/settings`).then(unwrap<FriendshipSettingResponse>),

  setCloseFriend: (friendId: UUID, isCloseFriend: boolean, note = "Bạn thân") =>
    apiClient
      .patch(`/api/v1/friends/${friendId}/settings`, { isCloseFriend, note })
      .then(unwrap<FriendshipSettingResponse>),

  getGroupLabelPresets: () =>
    apiClient.get("/api/v1/conversations/group-labels").then(unwrap<ConversationGroupLabelPresetResponse[]>),

  getConversationGroupLabel: (conversationId: UUID) =>
    apiClient
      .get(`/api/v1/conversations/${conversationId}/group-label`)
      .then(unwrap<ConversationGroupLabelResponse>),

  setConversationGroupLabel: (conversationId: UUID, groupLabel: GroupLabelCode | null) =>
    apiClient
      .patch(`/api/v1/conversations/${conversationId}/group-label`, { groupLabel })
      .then(unwrap<ConversationGroupLabelResponse>),

  getConversationsByGroupLabel: (groupLabel: GroupLabelCode) =>
    apiClient
      .get("/api/v1/conversations", { params: { archived: false, groupLabel } })
      .then(unwrap<ConversationResponse[]>),
};
```

## 6. FE Notes

- `MessageType.SYSTEM` là nguồn duy nhất để render system log trong timeline. Không cần gọi API riêng.
- Pin/unpin và reminder create/update/delete/cancel sẽ tự sinh system message từ BE.
- Presence dùng REST để load ban đầu, realtime dùng `/topic/presence`.
- Voice message flow: upload audio attachment, send `messageType: "AUDIO"`, sau đó gọi STT/TTS khi user cần.
- Cloud không còn IoT. My Documents chỉ gồm All, Images, Files, Links, Texts, Manual.
