# LaoAds — Hợp đồng API v1 (đợt 1)

Base URL: `{host}/api/v1` (BE context-path `/api`, controller prefix `/v1`). Dev: `http://localhost:8095/api/v1`.

Tài liệu này là **nguồn chung** cho BE (`laoads-backend`) và FE (`laoads-web`). Khi BE chạy được, springdoc sinh `docs/openapi.yaml` → FE `npm run gen:api`. Cho tới lúc đó, FE type tay theo file này (`src/api/types.ts`).

## 1. Quy ước chung

### Envelope (giữ đúng định dạng SRS LaoAds)

Thành công:
```json
{ "success": true, "data": <T>, "message": null, "statusCode": "OK" }
```
Danh sách phân trang (page bắt đầu từ 0):
```json
{ "success": true, "data": [<T>...], "statusCode": "OK",
  "metaData": { "totalPages": 3, "currentPage": 0, "pageSize": 10, "totalItems": 25 } }
```
Lỗi (HTTP status đúng ngữ nghĩa 400/401/403/404/409/500):
```json
{ "success": false, "data": null, "message": "Thông báo đã dịch theo Accept-Language",
  "statusCode": "MEMBER_DUPLICATE", "requestId": "a1b2c3", "errors": [{ "field": "role", "message": "..." }] }
```
`statusCode` lỗi chuẩn: `UNAUTHORIZED` (401), `TOKEN_EXPIRED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `VALIDATION_ERROR` (400, có `errors`), `INVALID_PARAM` (400), `BAD_REQUEST_BODY` (400), `CONFLICT` (409), `INTERNAL_ERROR` (500), cộng các mã nghiệp vụ ở từng mục.

### Header
- `Authorization: Bearer <accessToken>` — mọi endpoint trừ `/auth/login`, `/auth/refresh`, `/auth/vsa/*`.
- `X-Workspace-Id: <id>` — bắt buộc với endpoint **thuộc workspace** (đánh dấu 🏢). Thiếu → 400 `WORKSPACE_REQUIRED`; user không phải thành viên ACTIVE (và không phải SUPER_ADMIN) → 403.
- `Accept-Language: vi | en | lo` — chọn ngôn ngữ `message`.

### Kiểu dữ liệu
- `id`: số nguyên (Long) dạng JSON number.
- Thời gian: ISO-8601 có offset, vd `2026-11-15T09:30:00+07:00`.
- JSON field: camelCase.

## 2. Phân quyền

- **System role** (trên user): `SUPER_ADMIN` | `USER`. SUPER_ADMIN qua mọi kiểm tra quyền, kể cả không là thành viên workspace.
- **Workspace role** (trên thành viên): `OWNER` | `ADMIN` | `MEMBER`.
- **Permission** (ma trận role → permission, SUPER_ADMIN sửa được):

| Code | OWNER | ADMIN | MEMBER |
|---|---|---|---|
| `WORKSPACE_VIEW` | ✔ | ✔ | ✔ |
| `WORKSPACE_UPDATE` | ✔ | ✔ | |
| `MEMBER_MANAGE` | ✔ | ✔ | |
| `CAMPAIGN_VIEW` | ✔ | ✔ | ✔ |
| `CAMPAIGN_CREATE` | ✔ | ✔ | ✔ |
| `CAMPAIGN_UPDATE` | ✔ | ✔ | ✔ |
| `CAMPAIGN_SUBMIT` | ✔ | ✔ | ✔ |
| `CAMPAIGN_APPROVE` | ✔ | ✔ | |
| `CAMPAIGN_DELETE` | ✔ | ✔ | |
| `SEGMENT_MANAGE` | ✔ | ✔ | ✔ |
| `SECRET_KEY_MANAGE` | ✔ | | |
| `REPORT_VIEW` | ✔ | ✔ | ✔ |

Quy tắc thành viên (SRS 3.4): chỉ OWNER được gán/thu hồi vai trò OWNER; không đổi vai trò của chính mình; không gỡ OWNER; không tự gỡ mình; không thêm trùng.

## 3. Auth

| Method | Path | Body | Trả về `data` |
|---|---|---|---|
| POST | `/auth/login` | `{ username, password }` | `AuthTokens` |
| POST | `/auth/refresh` | `{ refreshToken }` | `AuthTokens` (refresh token xoay vòng, token cũ bị thu hồi) |
| POST | `/auth/logout` | `{ refreshToken }` | `null` |
| GET | `/auth/vsa/config` | — | `{ enabled: boolean, loginUrl: string \| null }` |
| POST | `/auth/vsa/login` | `{ ticket, service }` | `AuthTokens` — 400 `VSA_DISABLED` nếu chưa cấu hình |
| POST | `/auth/change-password` | `{ oldPassword, newPassword }` | `null` — sai mật khẩu cũ → **400** `WRONG_PASSWORD` (không 401) |
| GET | `/me` | — | `MeResponse` |

```ts
AuthTokens = { accessToken: string; refreshToken: string; expiresIn: number /*giây*/; user: UserDto }
UserDto = { id: number; username: string; fullName: string | null; email: string | null; phone: string | null;
            systemRole: 'SUPER_ADMIN' | 'USER'; status: 'ACTIVE' | 'LOCKED'; lastLoginAt: string | null; createdAt: string }
MeResponse = { user: UserDto; workspaces: MyWorkspace[] }
MyWorkspace = { id: number; code: string; name: string; status: 'ACTIVE' | 'LOCKED';
                role: 'OWNER' | 'ADMIN' | 'MEMBER' | null /* null khi SUPER_ADMIN không là thành viên */;
                permissions: string[] /* SUPER_ADMIN: toàn bộ */ }
```
Access token 30 phút; refresh token 7 ngày (lưu hash). 401 `TOKEN_EXPIRED` khi access token hết hạn → FE gọi `/auth/refresh` một lần rồi gửi lại request.

## 4. Workspace

| Method | Path | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/workspaces/my` | đăng nhập | = `MeResponse.workspaces` (chỉ ACTIVE) |
| GET | `/workspaces/{id}` | thành viên hoặc SUPER_ADMIN | `Workspace` |
| PUT | `/workspaces/{id}` | `WORKSPACE_UPDATE` | body `{ name, description, contactName, contactEmail, contactPhone }` |
| GET | `/admin/workspaces?page&size&query&status` | SUPER_ADMIN | page `Workspace` (+ `memberCount`) |
| POST | `/admin/workspaces` | SUPER_ADMIN | `{ code, name, description?, ownerUsername }` → tạo + gán OWNER. Trùng code → 409 `WORKSPACE_CODE_DUPLICATE` |
| PATCH | `/admin/workspaces/{id}/status` | SUPER_ADMIN | `{ status: 'ACTIVE' \| 'LOCKED' }` |

```ts
Workspace = { id: number; code: string; name: string; description: string | null; status: 'ACTIVE' | 'LOCKED';
              contactName: string | null; contactEmail: string | null; contactPhone: string | null;
              memberCount: number; createdAt: string; updatedAt: string | null }
```

## 5. Thành viên 🏢 (`{id}` trong path phải trùng `X-Workspace-Id`)

| Method | Path | Quyền | Body / Query |
|---|---|---|---|
| GET | `/workspaces/{id}/members` | `WORKSPACE_VIEW` | `?page&size&query&role&status` — query tìm theo tên/username/email |
| POST | `/workspaces/{id}/members` | `MEMBER_MANAGE` | `{ identifier /* username, email hoặc SĐT */, role }` → 404 `USER_NOT_FOUND`, 409 `MEMBER_DUPLICATE`, 403 `OWNER_ONLY` |
| PUT | `/workspaces/{id}/members/{memberId}/role` | `MEMBER_MANAGE` | `{ role }` → 400 `CANNOT_CHANGE_SELF`, 403 `OWNER_ONLY` |
| DELETE | `/workspaces/{id}/members/{memberId}` | `MEMBER_MANAGE` | → 400 `CANNOT_REMOVE_OWNER` / `CANNOT_REMOVE_SELF` |

```ts
Member = { id: number; userId: number; username: string; fullName: string | null; email: string | null; phone: string | null;
           role: 'OWNER' | 'ADMIN' | 'MEMBER'; status: 'ACTIVE' | 'REVOKED'; joinedAt: string }
```

## 6. Quản trị user & ma trận quyền (SUPER_ADMIN)

| Method | Path | Body |
|---|---|---|
| GET | `/admin/users?page&size&query&status&systemRole` | page `UserDto` |
| POST | `/admin/users` | `{ username, fullName, email?, phone?, password, systemRole }` → 409 `USERNAME_DUPLICATE` |
| PUT | `/admin/users/{id}` | `{ fullName, email?, phone?, systemRole }` |
| PATCH | `/admin/users/{id}/status` | `{ status: 'ACTIVE' \| 'LOCKED' }` — không tự khoá mình (400 `CANNOT_LOCK_SELF`) |
| POST | `/admin/users/{id}/reset-password` | `{ newPassword }` |
| GET | `/admin/permissions` | `PermissionDef[]` = `{ code, group: 'WORKSPACE'\|'MEMBER'\|'CAMPAIGN'\|'SEGMENT'\|'SECRET_KEY'\|'REPORT', description }` |
| GET | `/admin/roles` | `RoleDef[]` = `{ code: 'OWNER'\|'ADMIN'\|'MEMBER', name, permissions: string[] }` |
| PUT | `/admin/roles/{code}/permissions` | `{ permissions: string[] }` → `RoleDef` |

## 7. Bảng giá (Quản lý chi phí)

Đọc: mọi user đăng nhập. Ghi: SUPER_ADMIN. Một thời điểm chỉ có **1** bảng `LOCKED` (đang áp dụng); khoá bảng mới → bảng LOCKED cũ chuyển `ENDED`.

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/price-quotes?page&size&status` | page `PriceQuote` (sort createdAt desc) |
| GET | `/price-quotes/active` | bảng LOCKED hiện hành hoặc `null` |
| GET | `/price-quotes/{id}` | `PriceQuote` |
| POST | `/price-quotes` | `{ name, note?, items: PriceItemInput[] }` → tạo `DRAFT` |
| PUT | `/price-quotes/{id}` | chỉ khi `DRAFT`, ngược lại 409 `PRICE_QUOTE_NOT_DRAFT` |
| POST | `/price-quotes/{id}/lock` | `DRAFT` → `LOCKED` |
| DELETE | `/price-quotes/{id}` | chỉ `DRAFT` |

```ts
ActionType = 'TOP_UP' | 'ADD_MONEY' | 'ADD_PROMOTION' | 'ADD_DATA' | 'ADD_SMS' | 'ADD_CALL_MINUTES' | 'ADD_PACKAGE'
           | 'REGISTER_CRBT' | 'REGISTER_LAOTV' | 'NOTIFICATION_SMS' | 'NOTIFICATION_EMAIL' | 'NOTIFICATION_WHATSAPP' | 'NOTIFICATION_USSD'
PriceItemInput = { actionType: ActionType; unitPrice: number; timePrice: number; timePricePercentage: boolean }
PriceQuote = { id: number; name: string; note: string | null; status: 'DRAFT' | 'LOCKED' | 'ENDED'; currency: 'LAK';
               items: PriceItemInput[]; lockedAt: string | null; lockedBy: string | null;
               createdAt: string; createdBy: string | null; updatedAt: string | null }
```

## 8. Nhật ký hệ thống

| Method | Path | Quyền |
|---|---|---|
| GET | `/audit-logs?page&size&actor&actionType&entityType&from&to` | SUPER_ADMIN: tất cả; còn lại cần `REPORT_VIEW` + 🏢 (chỉ log của workspace đó) |
| GET | `/audit-logs/export?…cùng filter` | như trên; trả file `.xlsx` (không bọc envelope) |

```ts
AuditLog = { id: number; actor: string; actionType: string; entityType: string; entityId: string | null;
             summary: string | null; detailJson: string | null; workspaceId: number | null; ip: string | null; createdAt: string }
```
`actionType`: `LOGIN | CREATE | UPDATE | DELETE | LOCK | UNLOCK | APPROVE | REJECT | SUBMIT | RESET_PASSWORD | IMPORT | EXPORT | CHANGE_ROLE | ...`

## 9. Chưa có BE ở đợt 1 (FE dùng MSW mock theo shape SRS)

`/dashboard/*`, `/campaigns*`, `/campaign-groups*`, `/campaign-approvals*`, `/segments*`, `/settings/sender-numbers*`, `/settings/notification`. Shape tham chiếu SRS mục 3.1–3.5 và `src/mocks/fixtures` trong FE.
