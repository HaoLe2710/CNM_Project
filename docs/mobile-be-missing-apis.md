# Mobile FE -> BE Missing APIs (Settings/Profile/Cloud/Chat Group)

Date: 2026-05-30
Project: ChatMobileUI

## 1) Profile / Services cards

### 1.1 zStyle
Current FE has entry point but no BE contract.

Required endpoints:
- GET `/api/v1/zstyle/me`
- PATCH `/api/v1/zstyle/me`

Suggested response:
```json
{
  "themeId": "default",
  "accentColor": "#1570EF",
  "coverFrame": "none",
  "updatedAt": "2026-05-30T10:00:00Z"
}
```

### 1.2 ZaloPay / wallet module
Current FE has menu but no BE contract in current docs.

Required endpoints (or redirect contract):
- GET `/api/v1/wallet/overview`
- GET `/api/v1/wallet/deeplink`

### 1.3 Ringback tone / Nhạc chờ
Required endpoints:
- GET `/api/v1/ringback/me`
- PATCH `/api/v1/ringback/me`

## 2) Data on device (dữ liệu trên máy)

FE is using these endpoints now:
- GET `/api/v1/users/storage/summary`
- GET `/api/v1/users/storage/large-files?limit=`
- POST `/api/v1/users/storage/cache/cleanup`
- POST `/api/v1/users/storage/large-files/cleanup`
- POST `/api/v1/users/storage/chat-data/cleanup`

Need BE confirm official contract and payload schema in docs. If not available, provide equivalent endpoints.

Additionally requested behavior from product:
- Show media/files already sent from this phone to chat groups.

Suggested endpoint:
- GET `/api/v1/users/storage/sent-media?scope=GROUP&page=0&size=50`

Suggested response item:
```json
{
  "id": "uuid",
  "conversationId": "uuid",
  "conversationName": "Nhóm A",
  "messageId": 123,
  "fileName": "video_2026.mp4",
  "mimeType": "video/mp4",
  "sizeBytes": 12345678,
  "thumbnailUrl": "https://...",
  "createdAt": "2026-05-30T10:00:00Z"
}
```

## 3) Privacy settings coverage

Current FE maps privacy toggles to `/api/v1/users/settings`.
Need BE confirm all keys are persisted and supported:
- `privacy.showOnlineStatus`
- `privacy.readReceipts`
- `privacy.allowFriendRequests`
- `contacts.searchableByPhone`
- `contacts.searchableByEmail`

If some keys are not supported, BE please provide either:
- supported key list, or
- dedicated endpoints for unsupported keys.

## 4) Cloud/My Documents

Current FE integrated with:
- GET `/api/v1/cloud/files`
- GET `/api/v1/cloud/trash`
- POST `/api/v1/cloud/files/upload`
- POST `/api/v1/cloud/folders`
- PATCH `/api/v1/cloud/files/{fileId}`
- DELETE `/api/v1/cloud/files/{fileId}`
- POST `/api/v1/cloud/files/{fileId}/restore`
- DELETE `/api/v1/cloud/files/{fileId}/permanent`
- POST `/api/v1/cloud/files/{fileId}/send-to-conversation`
- GET `/api/v1/cloud/storage/summary`

Still missing for full UX parity:
- Server-side type for `Link` tab.
- Server-side type for `Thủ công` tab.

Suggested enum additions:
- `LINK`
- `MANUAL`

## 5) Group chat system timeline

From product screenshots, FE needs timeline system rows for:
- pin/unpin message
- reminder create/update/delete/cancel in chat timeline

Current BE docs still list these as missing. Please provide system message generation contract:
- message type: `SYSTEM`
- normalized payload or resolved text format.

## 6) IoT image analysis details

Still missing for screen `ImageAnalysisDetails`:
- GET `/api/v1/cloud/files/{fileId}/analysis`
- POST `/api/v1/cloud/files/{fileId}/analysis/run`

---
This file is generated from FE integration gap review after applying available APIs.
