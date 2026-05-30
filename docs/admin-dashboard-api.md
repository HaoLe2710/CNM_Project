# Admin Dashboard API (FE Integration)

Base path: `/api/v1/admin/dashboard`  
Auth: `Bearer accessToken` (role `ADMIN`)  
Realtime: STOMP/SockJS subscribe at:
- `/topic/admin/dashboard`
- `/topic/admin/reports`
- `/topic/admin/users`

## 1) Dashboard Summary
`GET /api/v1/admin/dashboard/summary`

Response `data`:
- `totalUsers`
- `activeUsers`
- `bannedUsers`
- `totalPosts`
- `totalPublicVideos`
- `pendingReports`
- `reportsLast24h`
- `alerts[]`
- `generatedAt`

## 2) Alerts
`GET /api/v1/admin/dashboard/alerts?size=20`

Response `data[]`:
- `type`: `REPORT|USER|POST`
- `severity`: `LOW|MEDIUM|HIGH`
- `title`
- `detail`
- `createdAt`

## 3) User Management
`GET /api/v1/admin/dashboard/users?keyword=&status=ALL|BANNED&page=0&size=20`

Response `data[]`:
- `userId`
- `username`
- `displayName`
- `email`
- `phone`
- `avatarUrl`
- `createdAt`
- `bannedUntil`
- `bannedNow`

### Ban/Unban User
`PATCH /api/v1/admin/dashboard/users/{userId}/moderation`

Body (ban):
```json
{
  "action": "BAN",
  "reason": "Spam",
  "banHours": 72
}
```

Body (unban):
```json
{
  "action": "UNBAN",
  "reason": "Reviewed"
}
```

## 4) Report Moderation Queue
`GET /api/v1/admin/dashboard/reports?status=PENDING|RESOLVED&targetType=USER|POST|MOMENT|MESSAGE&page=0&size=20`

Response `data[]`:
- `reportId`
- `targetType`
- `targetId`
- `reason`
- `actionTaken`
- `reporterDisplayName`
- `reporterUsername`
- `createdAt`
- `resolvedAt`

### Resolve Report
`PATCH /api/v1/admin/dashboard/reports/{reportId}/resolve`

Body:
```json
{
  "action": "DISMISS|WARN_USER|BAN_USER|REMOVE_POST|REMOVE_MOMENT|REMOVE_MESSAGE",
  "note": "optional",
  "banHours": 24
}
```

## 5) Logs
`GET /api/v1/admin/dashboard/logs?scope=ALL|SECURITY|ACTIVITY&size=50`

Response `data[]`:
- `source`: `SECURITY|ACTIVITY`
- `id`
- `userId`
- `eventType`
- `title`
- `detail`
- `metadata`
- `createdAt`

## 6) Realtime Event Contract
Payload format:
```json
{
  "type": "ADMIN_DASHBOARD_UPDATED|ADMIN_REPORT_CREATED|ADMIN_REPORT_RESOLVED|ADMIN_USER_MODERATED",
  "payload": {
    "...": "..."
  },
  "occurredAt": "2026-05-13T12:34:56Z"
}
```

FE handling suggestion:
- On `ADMIN_REPORT_CREATED|ADMIN_REPORT_RESOLVED` => refresh report list + summary.
- On `ADMIN_USER_MODERATED` => refresh user list + summary.
- On any admin event => refresh alerts.

## 7) User-side Report API (source of moderation queue)
`POST /api/v1/reports`

Body:
```json
{
  "targetType": "USER|POST|MOMENT|MESSAGE",
  "targetId": "uuid-or-messageId",
  "reason": "Nội dung vi phạm"
}
```

Response `data`:
- `id`
- `targetType`
- `targetId`
- `reason`
- `createdAt`
