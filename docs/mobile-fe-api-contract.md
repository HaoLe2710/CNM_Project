# Mobile FE API Contract

Date: 2026-05-30

All endpoints require the normal mobile bearer token unless noted otherwise. All responses are wrapped by `ApiResponse<T>`:

```json
{
  "success": true,
  "code": "OK",
  "message": "OK",
  "data": {},
  "errors": null,
  "meta": {
    "requestId": "uuid",
    "timestamp": "2026-05-30T12:00:00Z"
  }
}
```

## 1. Profile Service Cards

### zStyle

`GET /api/v1/zstyle/me`

Response `data`:

```json
{
  "enabled": false,
  "themeId": null,
  "themeName": "Mặc định",
  "backgroundUrl": null,
  "accentColor": "#0068ff"
}
```

`PATCH /api/v1/zstyle/me`

Request:

```json
{
  "enabled": true,
  "themeId": "blue-basic",
  "themeName": "Blue Basic",
  "backgroundUrl": "https://cdn.example.com/bg.png",
  "accentColor": "#0068ff"
}
```

### Wallet

`GET /api/v1/wallet/overview`

Response `data`:

```json
{
  "enabled": false,
  "provider": "ZALOPAY",
  "linked": false,
  "maskedPhone": "******1234",
  "balance": null,
  "currency": "VND"
}
```

`GET /api/v1/wallet/deeplink?action=OPEN`

Response `data`:

```json
{
  "provider": "ZALOPAY",
  "action": "OPEN",
  "deeplink": "zalopay://cnm-project?action=OPEN&userId=...",
  "fallbackUrl": "https://zalopay.vn/?action=OPEN&userId=..."
}
```

### Ringback

`GET /api/v1/ringback/me`

Response `data`:

```json
{
  "enabled": false,
  "toneId": null,
  "toneName": null,
  "artist": null,
  "previewUrl": null
}
```

`PATCH /api/v1/ringback/me`

Request:

```json
{
  "enabled": true,
  "toneId": "tone-001",
  "toneName": "Nhạc chờ",
  "artist": "Unknown",
  "previewUrl": "https://cdn.example.com/tone.mp3"
}
```

## 2. Data On Device

Existing endpoints:

- `GET /api/v1/users/storage/summary`
- `GET /api/v1/users/storage/large-files?limit=20`
- `POST /api/v1/users/storage/cache/cleanup`
- `POST /api/v1/users/storage/large-files/cleanup`
- `POST /api/v1/users/storage/chat-data/cleanup`

New sent media endpoint:

`GET /api/v1/users/storage/sent-media?scope=GROUP&page=0&size=50`

`scope`: `ALL`, `GROUP`, `PRIVATE`.

Response `data` is a Spring page:

```json
{
  "content": [
    {
      "id": 1,
      "messageId": 123,
      "conversationId": "uuid",
      "conversationName": "Nhóm A",
      "fileUrl": "https://cdn.example.com/file.jpg",
      "thumbnailUrl": "https://cdn.example.com/file.jpg",
      "name": "file.jpg",
      "sizeBytes": 16812,
      "sizeMb": 0.02,
      "type": "IMAGE",
      "createdAt": "2026-05-30T12:00:00Z"
    }
  ],
  "number": 0,
  "size": 50,
  "totalElements": 1,
  "totalPages": 1
}
```

## 3. Privacy Settings

Use existing endpoint:

- `GET /api/v1/users/settings`
- `PATCH /api/v1/users/settings`

Supported keys confirmed:

- `privacy.showOnlineStatus`
- `privacy.readReceipts`
- `privacy.allowFriendRequests`
- `contacts.searchableByPhone`
- `contacts.searchableByEmail`

Patch example:

```json
{
  "settings": {
    "privacy": {
      "showOnlineStatus": true,
      "readReceipts": true,
      "allowFriendRequests": true
    },
    "contacts": {
      "searchableByPhone": true,
      "searchableByEmail": true
    }
  }
}
```

## 3.1 Friend Birthdays

`GET /api/v1/friends/birthdays?upcomingDays=30`

Returns friends whose birthdays fall from today through the next `upcomingDays` days. `upcomingDays` is clamped to `0..366`.

Response `data`:

```json
[
  {
    "userId": "uuid",
    "displayName": "Trúc Ly",
    "avatarUrl": "https://cdn.example.com/avatar.jpg",
    "birthDate": "2002-05-19",
    "nextBirthday": "2026-05-19",
    "daysUntil": 0,
    "ageTurning": 24
  }
]
```

## 4. Cloud / My Documents

Existing endpoints:

- `GET /api/v1/cloud/files?type=IMAGE&page=0&size=50`
- `GET /api/v1/cloud/trash`
- `POST /api/v1/cloud/files/upload`
- `POST /api/v1/cloud/folders`
- `PATCH /api/v1/cloud/files/{fileId}`
- `DELETE /api/v1/cloud/files/{fileId}`
- `POST /api/v1/cloud/files/{fileId}/restore`
- `DELETE /api/v1/cloud/files/{fileId}/permanent`
- `POST /api/v1/cloud/files/{fileId}/send-to-conversation`
- `GET /api/v1/cloud/storage/summary`

New server-side cloud types:

- `LINK`
- `MANUAL`

Create link:

`POST /api/v1/cloud/links`

```json
{
  "name": "Office form",
  "url": "https://forms.office.com/r/abc",
  "parentFolderId": null
}
```

Create manual item:

`POST /api/v1/cloud/manual-items`

```json
{
  "name": "Ghi chú thủ công",
  "content": "Nội dung ghi chú",
  "parentFolderId": null
}
```

Filter examples:

- `GET /api/v1/cloud/files?type=LINK`
- `GET /api/v1/cloud/files?type=MANUAL`

## 5. Chat Timeline System Messages

System rows are emitted as normal message timeline items:

- `messageType`: `SYSTEM`
- `content`: resolved display text
- Delivered through existing message list and realtime `MESSAGE_CREATED` event.

Generated events:

- Pin message: `{actorName} đã ghim 1 tin nhắn ...`
- Unpin message: `{actorName} bỏ ghim 1 tin nhắn ...`
- Reminder create: `{actorName} tạo nhắc hẹn {title}`
- Reminder update: `{actorName} cập nhật nhắc hẹn {title}`
- Reminder cancel: `{actorName} hủy nhắc hẹn {title}`
- Reminder delete: `{actorName} xóa nhắc hẹn {title}`

## 6. Polls / Bình Chọn

All poll endpoints require `x-user-id` header like the existing message APIs.

### List Polls In Conversation

`GET /api/v1/conversations/{conversationId}/polls`

### Create Poll

`POST /api/v1/conversations/{conversationId}/polls`

Request:

```json
{
  "question": "Tối nay ăn gì?",
  "options": ["Lẩu", "Cơm tấm", "Bún bò"],
  "multipleChoice": false,
  "anonymous": false,
  "expiresAt": "2026-06-01T12:00:00Z"
}
```

Rules:

- `options`: 2..20 distinct options.
- `multipleChoice=false`: mỗi user chỉ có 1 vote.
- `anonymous=true`: BE không trả `voterIds`, chỉ trả count.

### Detail / Update / Delete / Close

- `GET /api/v1/polls/{pollId}`
- `PATCH /api/v1/polls/{pollId}`
- `DELETE /api/v1/polls/{pollId}`
- `POST /api/v1/polls/{pollId}/close`

Update request:

```json
{
  "question": "Tối nay ăn món gì?",
  "multipleChoice": true,
  "anonymous": false,
  "expiresAt": "2026-06-01T12:00:00Z"
}
```

Only creator can update/delete/close. Poll with existing votes cannot be edited.

### Option CRUD

- `POST /api/v1/polls/{pollId}/options`
- `PATCH /api/v1/polls/{pollId}/options/{optionId}`
- `DELETE /api/v1/polls/{pollId}/options/{optionId}`

Request:

```json
{
  "text": "Pizza"
}
```

Only creator can manage options. Poll must keep at least 2 options.

### Vote / Unvote

- `POST /api/v1/polls/{pollId}/options/{optionId}/votes`
- `DELETE /api/v1/polls/{pollId}/options/{optionId}/votes`

Response `data` for all poll APIs:

```json
{
  "id": "uuid",
  "conversationId": "uuid",
  "creatorId": "uuid",
  "question": "Tối nay ăn gì?",
  "multipleChoice": false,
  "anonymous": false,
  "status": "ACTIVE",
  "expiresAt": "2026-06-01T12:00:00Z",
  "closedAt": null,
  "totalVotes": 1,
  "myOptionIds": ["option-uuid"],
  "options": [
    {
      "id": "option-uuid",
      "text": "Lẩu",
      "position": 0,
      "voteCount": 1,
      "votedByMe": true,
      "voterIds": ["user-uuid"]
    }
  ],
  "createdAt": "2026-05-31T02:00:00Z",
  "updatedAt": null
}
```

Suggested FE service:

```ts
export type PollStatus = "ACTIVE" | "CLOSED";

export interface PollOptionResponse {
  id: string;
  text: string;
  position: number;
  voteCount: number;
  votedByMe: boolean;
  voterIds: string[];
}

export interface PollResponse {
  id: string;
  conversationId: string;
  creatorId: string;
  question: string;
  multipleChoice: boolean;
  anonymous: boolean;
  status: PollStatus;
  expiresAt?: string;
  closedAt?: string;
  totalVotes: number;
  myOptionIds: string[];
  options: PollOptionResponse[];
  createdAt: string;
  updatedAt?: string;
}

export const pollApi = {
  list: (conversationId: string) =>
    apiClient.get(`/api/v1/conversations/${conversationId}/polls`).then(unwrap<PollResponse[]>),
  create: (conversationId: string, body: {
    question: string;
    options: string[];
    multipleChoice?: boolean;
    anonymous?: boolean;
    expiresAt?: string;
  }) => apiClient.post(`/api/v1/conversations/${conversationId}/polls`, body).then(unwrap<PollResponse>),
  get: (pollId: string) =>
    apiClient.get(`/api/v1/polls/${pollId}`).then(unwrap<PollResponse>),
  update: (pollId: string, body: Partial<Pick<PollResponse, "question" | "multipleChoice" | "anonymous" | "expiresAt">>) =>
    apiClient.patch(`/api/v1/polls/${pollId}`, body).then(unwrap<PollResponse>),
  remove: (pollId: string) =>
    apiClient.delete(`/api/v1/polls/${pollId}`).then(unwrap<void>),
  close: (pollId: string) =>
    apiClient.post(`/api/v1/polls/${pollId}/close`).then(unwrap<PollResponse>),
  addOption: (pollId: string, text: string) =>
    apiClient.post(`/api/v1/polls/${pollId}/options`, { text }).then(unwrap<PollResponse>),
  updateOption: (pollId: string, optionId: string, text: string) =>
    apiClient.patch(`/api/v1/polls/${pollId}/options/${optionId}`, { text }).then(unwrap<PollResponse>),
  deleteOption: (pollId: string, optionId: string) =>
    apiClient.delete(`/api/v1/polls/${pollId}/options/${optionId}`).then(unwrap<PollResponse>),
  vote: (pollId: string, optionId: string) =>
    apiClient.post(`/api/v1/polls/${pollId}/options/${optionId}/votes`).then(unwrap<PollResponse>),
  unvote: (pollId: string, optionId: string) =>
    apiClient.delete(`/api/v1/polls/${pollId}/options/${optionId}/votes`).then(unwrap<PollResponse>),
};
```

## 7. Image Analysis Details

`GET /api/v1/cloud/files/{fileId}/analysis`

`POST /api/v1/cloud/files/{fileId}/analysis/run`

Response `data`:

```json
{
  "fileId": "uuid",
  "mediaUrl": "https://cdn.example.com/image.jpg",
  "timeCaptured": "2026-05-30T12:00:00Z",
  "uploadStatus": "Đã tải lên",
  "fileSizeLabel": "640x480 - 16812 bytes",
  "transmissionStatus": "Đã gửi",
  "analysisStatus": "Trạng thái phân tích: Phát hiện bệnh",
  "detectedDisease": "rust",
  "severityLevel": "Cao",
  "rawAnalysis": {
    "disease": "rust",
    "severity": "Cao",
    "confidence": 0.92
  },
  "createdAt": "2026-05-30T12:00:00Z",
  "updatedAt": "2026-05-30T12:00:00Z"
}
```
