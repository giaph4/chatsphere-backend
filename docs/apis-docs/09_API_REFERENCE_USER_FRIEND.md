# API REFERENCE — USER & FRIEND MODULE

**Base URL**: `http://localhost:8080` (dev) · **Prefix**: `/api/v1/users`, `/api/v1/friends`, `/api/v1/friend-requests`
**Swagger UI**: `/swagger-ui.html` · **OpenAPI JSON**: `/v3/api-docs`

Tài liệu này mô tả đầy đủ 16 endpoint của module User & Friend: request/response schema, validation,
mã lỗi, ví dụ `curl`, và các ca biên cần lưu ý. Đi kèm `08_PHASE2_USER_FRIEND_REPORT.md` (kiến trúc,
luồng, lý thuyết) và `07_API_REFERENCE_AUTH.md` (đăng ký/đăng nhập để lấy `access_token` dùng ở đây).

---

## MỤC LỤC

1. [Quy ước chung](#1-quy-ước-chung)
2. [Xác thực](#2-xác-thực)
3. Hồ sơ & tìm kiếm
   - `GET /users/me` — [Hồ sơ của tôi](#31-get-apiv1usersme)
   - `PUT /users/me` — [Cập nhật hồ sơ](#32-put-apiv1usersme)
   - `GET /users/{id}` — [Hồ sơ người khác](#33-get-apiv1usersid)
   - `GET /users/search` — [Tìm kiếm](#34-get-apiv1userssearch)
4. Cài đặt riêng tư
   - `GET /users/me/settings` — [Lấy cài đặt](#41-get-apiv1usersmesettings)
   - `PUT /users/me/settings` — [Cập nhật cài đặt](#42-put-apiv1usersmesettings)
5. Chặn người dùng
   - `POST /users/{id}/block` — [Chặn](#51-post-apiv1usersidblock)
   - `DELETE /users/{id}/block` — [Bỏ chặn](#52-delete-apiv1usersidblock)
6. Lời mời kết bạn
   - `POST /friend-requests` — [Gửi lời mời](#61-post-apiv1friend-requests)
   - `PUT /friend-requests/{id}/accept` — [Chấp nhận](#62-put-apiv1friend-requestsidaccept)
   - `PUT /friend-requests/{id}/reject` — [Từ chối](#63-put-apiv1friend-requestsidreject)
   - `DELETE /friend-requests/{id}` — [Thu hồi](#64-delete-apiv1friend-requestsid)
   - `GET /friend-requests/received` — [Danh sách lời mời đến](#65-get-apiv1friend-requestsreceived)
   - `GET /friend-requests/sent` — [Danh sách lời mời đã gửi](#66-get-apiv1friend-requestssent)
7. Bạn bè
   - `GET /friends` — [Danh sách bạn bè](#71-get-apiv1friends)
   - `DELETE /friends/{id}` — [Hủy kết bạn](#72-delete-apiv1friendsid)
8. [Bảng tổng hợp mã lỗi](#8-bảng-tổng-hợp-mã-lỗi)
9. [Kịch bản test end-to-end](#9-kịch-bản-test-end-to-end)
10. [Postman / curl collection](#10-postman--curl-collection)

---

## 1. QUY ƯỚC CHUNG

Kế thừa toàn bộ quy ước của Auth module (`07_API_REFERENCE_AUTH.md` §1): phong bì `ApiResponse`,
JSON **snake_case**, `Content-Type: application/json`, thời gian ISO-8601 UTC.

### 1.1. Phân trang — cursor không dùng ở đây, dùng `page`/`size` chuẩn Spring Data

Khác với tin nhắn (Phase 3, cursor-based), danh sách bạn bè/lời mời dùng **phân trang offset chuẩn**
vì dữ liệu không tăng trưởng nhanh và không cần tối ưu tới mức đó:

```
GET /api/v1/friends?page=0&size=20&sort=createdAt,desc
```

| Query param | Mặc định | Ghi chú |
|---|---|---|
| `page` | `0` | 0-indexed |
| `size` | `20` | |
| `sort` | (không sort) | cú pháp `field,asc\|desc` |

### 1.2. Contract phân trang trong response — `PageResponse<T>`

Response của mọi endpoint trả danh sách đều bọc trong cấu trúc **ổn định của riêng dự án**, KHÔNG
phải cấu trúc `Page` mặc định của Spring Data (Spring cảnh báo "Serializing PageImpl instances as-is
is not supported" — cấu trúc đó là chi tiết nội bộ, có thể đổi giữa các version):

```jsonc
{
  "success": true,
  "data": {
    "items": [ /* mảng phần tử của trang này */ ],
    "page": 0,
    "size": 20,
    "total_elements": 3,
    "total_pages": 1,
    "has_next": false
  },
  "error": null,
  "timestamp": "2026-09-05T08:00:00.000Z"
}
```

### 1.3. Hai lớp hiển thị User — đọc kỹ trước khi tích hợp frontend

| DTO | Dùng ở | Có `email`? | Có `date_of_birth`, `status`? |
|---|---|---|---|
| `UserProfileResponse` | `GET /users/me` (chỉ chính chủ) | ✅ | ✅ |
| `UserSummaryResponse` | mọi nơi khác (search, friends, sender lời mời, `GET /users/{id}`) | ❌ | ❌ |

Đây là **giới hạn cố ý** ở tầng thiết kế, không phải thiếu sót: `UserSummaryResponse` không có chỗ
nào để chứa email, nên nếu bạn cần email của người khác cho một tính năng mới, đó là dấu hiệu cần
dừng lại hỏi lại yêu cầu — không phải đi tìm cách "lách" qua endpoint khác.

---

## 2. XÁC THỰC

**Toàn bộ 16 endpoint của module này đều yêu cầu đăng nhập** (khác Auth module — nơi hầu hết là
public). Lấy `access_token` từ `POST /api/v1/auth/login` (xem `07_API_REFERENCE_AUTH.md` §3.3).

```
Authorization: Bearer <access_token>
```

Thiếu header hoặc token sai/hết hạn → **401 `UNAUTHORIZED`**.

`currentUserId` (danh tính "tôi") luôn lấy từ token đã verify chữ ký, **không bao giờ** từ một field
trong body/query — bạn không thể mạo danh người khác bằng cách tự truyền `user_id` vào request.

---

## 3.1. `GET /api/v1/users/me`

Lấy hồ sơ đầy đủ của chính mình.

**Auth**: ✅ · **Thành công**: `200 OK`

### Response 200

```json
{
  "success": true,
  "data": {
    "id": "d2d00cea-70a4-47a4-afdd-5c54929ae01c",
    "email": "alice@example.com",
    "username": "alice_w",
    "display_name": "Alice Wonderland",
    "avatar_url": null,
    "bio": null,
    "date_of_birth": null,
    "status": "ACTIVE",
    "created_at": "2026-09-05T00:39:15.148Z"
  },
  "error": null,
  "timestamp": "2026-09-05T08:00:00.000Z"
}
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 401 | `UNAUTHORIZED` | Thiếu/sai/hết hạn token |
| 404 | `USER_NOT_FOUND` | User đã bị xóa mềm sau khi token được cấp (hiếm gặp) |

### curl

```bash
curl -s http://localhost:8080/api/v1/users/me -H "Authorization: Bearer $ACCESS"
```

---

## 3.2. `PUT /api/v1/users/me`

Cập nhật hồ sơ. **PUT thay thế toàn bộ** — không phải PATCH.

**Auth**: ✅ · **Thành công**: `200 OK`

### Request body

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `display_name` | string | ✅ | không rỗng, tối đa 100 ký tự |
| `bio` | string \| `null` | ❌ | tối đa 255 ký tự |
| `date_of_birth` | string (`YYYY-MM-DD`) \| `null` | ❌ | phải ở quá khứ |

```json
{ "display_name": "Alice W.", "bio": "Học Spring Boot mỗi ngày", "date_of_birth": "2000-01-15" }
```

### ⚠️ Ngữ nghĩa `null` — đọc kỹ trước khi tích hợp

`null` ở `bio`/`date_of_birth` nghĩa là **XÓA giá trị**, không phải "giữ nguyên giá trị cũ". Vì đây
là `PUT` (thay thế toàn bộ), client **luôn phải gửi đủ 3 field** mỗi lần gọi — cách làm đúng ở
frontend là điền sẵn form từ response của `GET /users/me` rồi cho user sửa, không phải chỉ gửi field
đã đổi.

```
Gửi { "display_name": "X", "bio": null }  → bio bị XÓA, dù trước đó có giá trị gì
KHÔNG gửi field "bio" trong JSON           → cũng bị hiểu là null → CŨNG bị xóa
```

### Response 200

Giống hệt schema của [`GET /users/me`](#31-get-apiv1usersme), phản ánh giá trị **sau khi** cập nhật.

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `display_name` rỗng, quá 100 ký tự, `bio` quá 255 ký tự, hoặc `date_of_birth` ở tương lai |
| 401 | `UNAUTHORIZED` | Thiếu/sai token |

### curl

```bash
curl -s -X PUT http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d '{"display_name":"Alice W.","bio":"Học Spring Boot mỗi ngày","date_of_birth":"2000-01-15"}'
```

---

## 3.3. `GET /api/v1/users/{id}`

Xem hồ sơ **rút gọn** của người khác — dùng khi đã có sẵn `id` (ví dụ từ danh sách bạn bè, kết quả
search) và cần load lại thông tin mới nhất.

**Auth**: ✅ · **Thành công**: `200 OK`

### Response 200

```json
{
  "success": true,
  "data": {
    "id": "906ff5e0-76da-4af5-9265-c53888e70ae8",
    "username": "bob_h",
    "display_name": "Bob Ho",
    "avatar_url": null,
    "bio": null
  },
  "error": null,
  "timestamp": "2026-09-05T08:00:00.000Z"
}
```

Chú ý: **không có `email`**, không có `date_of_birth`, không có `status` — xem §1.3.

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 404 | `USER_NOT_FOUND` | `id` không tồn tại hoặc đã bị xóa mềm |

Endpoint này **không kiểm tra quan hệ chặn** — xem được hồ sơ rút gọn của bất kỳ ai đang `ACTIVE`, kể
cả người đã chặn bạn hoặc bạn đã chặn (việc chặn chỉ ảnh hưởng tới **tương tác** — gửi lời mời, nhắn
tin ở Phase 3 — không ẩn hồ sơ công khai).

### curl

```bash
curl -s http://localhost:8080/api/v1/users/906ff5e0-76da-4af5-9265-c53888e70ae8 \
  -H "Authorization: Bearer $ACCESS"
```

---

## 3.4. `GET /api/v1/users/search`

Tìm user theo `username` hoặc `display_name`, kèm quan hệ giữa người tìm và từng kết quả.

**Auth**: ✅ · **Thành công**: `200 OK`

### Query params

| Param | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `q` | string | ✅ | không được rỗng/toàn khoảng trắng |
| `page`, `size`, `sort` | — | ❌ | xem §1.1 |

```
GET /api/v1/users/search?q=bob&page=0&size=20
```

### Response 200

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "user": {
          "id": "906ff5e0-76da-4af5-9265-c53888e70ae8",
          "username": "bob_h",
          "display_name": "Bob Ho",
          "avatar_url": null,
          "bio": null
        },
        "relationship": "NONE"
      }
    ],
    "page": 0, "size": 20, "total_elements": 1, "total_pages": 1, "has_next": false
  },
  "error": null,
  "timestamp": "2026-09-05T08:00:00.000Z"
}
```

### Giá trị của `relationship`

| Giá trị | Ý nghĩa | Nút gợi ý cho FE |
|---|---|---|
| `FRIEND` | Đã là bạn | "Nhắn tin" / "Hủy kết bạn" |
| `REQUEST_SENT` | Bạn đã gửi lời mời, đang chờ | "Thu hồi lời mời" |
| `REQUEST_RECEIVED` | Người này đã gửi lời mời cho bạn | "Chấp nhận" / "Từ chối" |
| `BLOCKED` | **Bạn** đã chặn người này | "Bỏ chặn" |
| `NONE` | Chưa có quan hệ gì | "Kết bạn" |

`SELF` không bao giờ xuất hiện — chính bạn bị loại khỏi kết quả tìm kiếm của chính mình.

### Ai KHÔNG xuất hiện trong kết quả

- Bản thân người tìm.
- User chưa `ACTIVE` (chưa xác thực email, đã khóa, đã tự vô hiệu hóa) hoặc đã xóa mềm.
- **Người đã chặn bạn** — dù bạn gõ đúng username của họ, họ sẽ không hiện ra. Ngược lại, người **bạn**
  chặn vẫn hiện bình thường (relationship = `BLOCKED`) để bạn còn thấy và bỏ chặn được.

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `q` rỗng hoặc chỉ có khoảng trắng |
| 401 | `UNAUTHORIZED` | Thiếu/sai token |

### Ghi chú vận hành

- Tìm kiếm hiện dùng `LIKE '%...%'` (chưa có index full-text — nợ kỹ thuật đã ghi ở báo cáo Phase 2,
  nâng cấp ở Phase 8.1). Với vài nghìn user thì tốc độ vẫn ổn.
- Ký tự đại diện của SQL LIKE (`%`, `_`) trong `q` được **tự động vô hiệu hóa** — gõ `%` để "tìm tất
  cả" sẽ **không** hoạt động, nó tìm đúng ký tự `%` theo nghĩa đen (chặn dump toàn bộ danh sách user).
- `relationship` được tính cho **cả trang** bằng 3 query gộp, không phải 1 query/kết quả — không có
  vấn đề hiệu năng khi tăng `size`.

### curl

```bash
curl -s "http://localhost:8080/api/v1/users/search?q=bob&page=0&size=20" \
  -H "Authorization: Bearer $ACCESS"
```

---

## 4.1. `GET /api/v1/users/me/settings`

Lấy cài đặt quyền riêng tư. **Tự tạo giá trị mặc định** nếu đây là lần đọc đầu tiên của user (không
cần bước "khởi tạo" riêng).

**Auth**: ✅ · **Thành công**: `200 OK`

### Response 200

```json
{
  "success": true,
  "data": {
    "online_visibility": "EVERYONE",
    "call_permission": "EVERYONE",
    "notification_enabled": true
  },
  "error": null,
  "timestamp": "2026-09-05T08:00:00.000Z"
}
```

Giá trị có thể của `online_visibility`/`call_permission`: `EVERYONE`, `FRIENDS_ONLY`, `NOBODY`.

### curl

```bash
curl -s http://localhost:8080/api/v1/users/me/settings -H "Authorization: Bearer $ACCESS"
```

---

## 4.2. `PUT /api/v1/users/me/settings`

Cập nhật cài đặt. PUT thay thế toàn bộ — cả 3 field đều **bắt buộc** (khác `UpdateProfileRequest`,
ở đây không có field nào optional).

**Auth**: ✅ · **Thành công**: `200 OK`

### Request body

| Field | Kiểu | Bắt buộc |
|---|---|---|
| `online_visibility` | `"EVERYONE"` \| `"FRIENDS_ONLY"` \| `"NOBODY"` | ✅ |
| `call_permission` | `"EVERYONE"` \| `"FRIENDS_ONLY"` \| `"NOBODY"` | ✅ |
| `notification_enabled` | boolean | ✅ |

```json
{ "online_visibility": "FRIENDS_ONLY", "call_permission": "NOBODY", "notification_enabled": false }
```

> **Vì sao `notification_enabled` không có giá trị mặc định khi thiếu field**: request dùng kiểu
> `Boolean` (wrapper), không phải `boolean` (primitive) — thiếu field sẽ bị từ chối `VALIDATION_ERROR`
> thay vì âm thầm hiểu thành `false`. Luôn gửi đủ 3 field.

### Response 200

Giống hệt schema của [`GET .../settings`](#41-get-apiv1usersmesettings), phản ánh giá trị mới.

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu field, hoặc giá trị enum không hợp lệ (không phải 1 trong 3 giá trị cho phép) |
| 401 | `UNAUTHORIZED` | Thiếu/sai token |

### curl

```bash
curl -s -X PUT http://localhost:8080/api/v1/users/me/settings \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d '{"online_visibility":"FRIENDS_ONLY","call_permission":"NOBODY","notification_enabled":false}'
```

---

## 5.1. `POST /api/v1/users/{id}/block`

Chặn người dùng `{id}`. Nếu 2 người đang là bạn, **quan hệ bạn bè bị hủy ngay lập tức** trong cùng
thao tác.

**Auth**: ✅ · **Thành công**: `200 OK`

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "2026-09-05T08:00:00.000Z" }
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `CANNOT_BLOCK_SELF` | `{id}` là chính bạn |
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 404 | `USER_NOT_FOUND` | `{id}` không tồn tại hoặc đã xóa mềm |
| 409 | `ALREADY_BLOCKED` | Đã chặn người này từ trước (gọi lại endpoint không idempotent — khác `unblock`) |

### Ghi chú vận hành

Sau khi chặn: người bị chặn **không biến mất khỏi** `GET /users/{id}` (vẫn xem được hồ sơ rút gọn),
nhưng **biến mất khỏi** kết quả `GET /users/search` của chính họ (họ không tìm thấy bạn — xem §3.4),
và mọi lời mời kết bạn giữa 2 người đều bị từ chối với `USER_BLOCKED` (§6.1).

### curl

```bash
curl -s -X POST http://localhost:8080/api/v1/users/906ff5e0-76da-4af5-9265-c53888e70ae8/block \
  -H "Authorization: Bearer $ACCESS"
```

---

## 5.2. `DELETE /api/v1/users/{id}/block`

Bỏ chặn. **Idempotent theo chủ ý** — bỏ chặn người chưa từng chặn vẫn trả `200`, không phải lỗi.

**Auth**: ✅ · **Thành công**: `200 OK` (luôn luôn, không phụ thuộc trạng thái trước đó)

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "2026-09-05T08:00:00.000Z" }
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 401 | `UNAUTHORIZED` | Thiếu/sai token |

Bỏ chặn **không** tự động khôi phục quan hệ bạn bè đã bị hủy lúc chặn — muốn kết bạn lại phải gửi lời
mời mới.

### curl

```bash
curl -s -X DELETE http://localhost:8080/api/v1/users/906ff5e0-76da-4af5-9265-c53888e70ae8/block \
  -H "Authorization: Bearer $ACCESS"
```

---

## 6.1. `POST /api/v1/friend-requests`

Gửi lời mời kết bạn.

**Auth**: ✅ · **Thành công**: `201 Created`

### Request body

| Field | Kiểu | Bắt buộc |
|---|---|---|
| `receiver_id` | UUID | ✅ |

```json
{ "receiver_id": "906ff5e0-76da-4af5-9265-c53888e70ae8" }
```

### Response 201

```json
{
  "success": true,
  "data": {
    "id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
    "sender": { "id": "...", "username": "alice_w", "display_name": "Alice W.", "avatar_url": null, "bio": null },
    "receiver": { "id": "...", "username": "bob_h", "display_name": "Bob Ho", "avatar_url": null, "bio": null },
    "status": "PENDING",
    "created_at": "2026-09-05T08:00:00.000Z"
  },
  "error": null,
  "timestamp": "2026-09-05T08:00:00.000Z"
}
```

### ⚠️ Trường hợp đặc biệt — lời mời chéo tự động thành bạn

Nếu Bob **đã** gửi lời mời cho Alice từ trước (đang `PENDING`) và Alice gọi endpoint này để gửi cho
Bob, hệ thống **không tạo lời mời thứ 2** — nó tự động coi đây là hành động **chấp nhận** lời mời của
Bob. Response vẫn `201`, nhưng `status` trong `data` sẽ là `"ACCEPTED"` ngay lập tức thay vì
`"PENDING"`, và 2 người đã là bạn ngay sau lệnh gọi này.

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu/sai định dạng `receiver_id` |
| 400 | `CANNOT_FRIEND_SELF` | `receiver_id` là chính bạn |
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 403 | `USER_BLOCKED` | Có quan hệ chặn ở **bất kỳ chiều nào** giữa 2 người — response **không phân biệt** bạn chặn họ hay họ chặn bạn (xem §1.3 báo cáo Phase 2 về lý do) |
| 404 | `USER_NOT_FOUND` | `receiver_id` không tồn tại hoặc đã xóa mềm |
| 409 | `ALREADY_FRIENDS` | 2 người đã là bạn từ trước |
| 409 | `FRIEND_REQUEST_ALREADY_SENT` | Đã có lời mời `PENDING` cho đúng cặp (sender, receiver) này — gọi lại lần 2 mà chưa được xử lý |

### curl

```bash
curl -s -X POST http://localhost:8080/api/v1/friend-requests \
  -H "Authorization: Bearer $ALICE_TOKEN" -H "Content-Type: application/json" \
  -d '{"receiver_id":"906ff5e0-76da-4af5-9265-c53888e70ae8"}'
```

---

## 6.2. `PUT /api/v1/friend-requests/{id}/accept`

Chấp nhận lời mời. **Chỉ người NHẬN** (`receiver`) mới gọi được.

**Auth**: ✅ · **Thành công**: `200 OK`

### Response 200

Giống schema của [`POST /friend-requests`](#61-post-apiv1friend-requests), với `status: "ACCEPTED"`.

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 403 | `USER_BLOCKED` | Có quan hệ chặn giữa 2 người kể từ lúc gửi lời mời (hiếm — thường xảy ra nếu 1 trong 2 chặn người kia sau khi lời mời đã gửi) |
| 404 | `FRIEND_REQUEST_NOT_FOUND` | `{id}` không tồn tại, **hoặc** bạn không phải người nhận của lời mời này (2 trường hợp trả **cùng một lỗi** — không tiết lộ sự tồn tại của request cho người ngoài cuộc) |
| 409 | `FRIEND_REQUEST_NOT_PENDING` | Lời mời đã được accept/reject/cancel từ trước (kể cả do chính bạn gọi accept 2 lần liên tiếp — double-click) |

### ⚠️ Double-click / 2 tab cùng accept

Gọi 2 lần liên tiếp (hoặc từ 2 tab) cho cùng `{id}` **không** tạo 2 quan hệ bạn bè hay lỗi 500 — lần
đầu trả `200`, lần sau trả **`409 FRIEND_REQUEST_NOT_PENDING`**. Được đảm bảo bằng ràng buộc ở tầng
database (compare-and-set UPDATE), không phụ thuộc frontend disable nút kịp thời hay không.

### curl

```bash
curl -s -X PUT http://localhost:8080/api/v1/friend-requests/3f2504e0-4f89-11d3-9a0c-0305e82c3301/accept \
  -H "Authorization: Bearer $BOB_TOKEN"
```

---

## 6.3. `PUT /api/v1/friend-requests/{id}/reject`

Từ chối lời mời. **Chỉ người NHẬN** mới gọi được. Bản ghi vẫn được **giữ lại** (status `REJECTED`) —
không xóa — nên người gửi vẫn gửi lại được lời mời mới sau này (partial unique index chỉ chặn khi
đang `PENDING`).

**Auth**: ✅ · **Thành công**: `200 OK`

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "2026-09-05T08:00:00.000Z" }
```

### Lỗi có thể gặp

Giống hệt [`accept`](#62-put-apiv1friend-requestsidaccept) — `FRIEND_REQUEST_NOT_FOUND` (sai actor
hoặc không tồn tại), `FRIEND_REQUEST_NOT_PENDING` (đã xử lý rồi).

### curl

```bash
curl -s -X PUT http://localhost:8080/api/v1/friend-requests/3f2504e0-4f89-11d3-9a0c-0305e82c3301/reject \
  -H "Authorization: Bearer $BOB_TOKEN"
```

---

## 6.4. `DELETE /api/v1/friend-requests/{id}`

Thu hồi lời mời đã gửi. **Chỉ người GỬI** (`sender`) mới gọi được — ngược vai với `accept`/`reject`.

**Auth**: ✅ · **Thành công**: `200 OK`

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "2026-09-05T08:00:00.000Z" }
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 404 | `FRIEND_REQUEST_NOT_FOUND` | `{id}` không tồn tại, hoặc bạn là **receiver** chứ không phải sender (hủy lời mời không phải quyền của người nhận — họ dùng `reject`) |
| 409 | `FRIEND_REQUEST_NOT_PENDING` | Đã được accept/reject/cancel từ trước |

### curl

```bash
curl -s -X DELETE http://localhost:8080/api/v1/friend-requests/3f2504e0-4f89-11d3-9a0c-0305e82c3301 \
  -H "Authorization: Bearer $ALICE_TOKEN"
```

---

## 6.5. `GET /api/v1/friend-requests/received`

Danh sách lời mời đang **chờ bạn duyệt** (bạn là `receiver`, `status = PENDING`).

**Auth**: ✅ · **Thành công**: `200 OK`

### Response 200

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
        "sender": { "id": "...", "username": "alice_w", "display_name": "Alice W.", "avatar_url": null, "bio": null },
        "receiver": { "id": "...", "username": "bob_h", "display_name": "Bob Ho", "avatar_url": null, "bio": null },
        "status": "PENDING",
        "created_at": "2026-09-05T08:00:00.000Z"
      }
    ],
    "page": 0, "size": 20, "total_elements": 1, "total_pages": 1, "has_next": false
  },
  "error": null,
  "timestamp": "2026-09-05T08:00:00.000Z"
}
```

### curl

```bash
curl -s http://localhost:8080/api/v1/friend-requests/received -H "Authorization: Bearer $BOB_TOKEN"
```

---

## 6.6. `GET /api/v1/friend-requests/sent`

Danh sách lời mời **bạn đã gửi**, đang chờ phản hồi (bạn là `sender`, `status = PENDING`).

**Auth**: ✅ · **Thành công**: `200 OK` · Schema response giống hệt [§6.5](#65-get-apiv1friend-requestsreceived).

### curl

```bash
curl -s http://localhost:8080/api/v1/friend-requests/sent -H "Authorization: Bearer $ALICE_TOKEN"
```

---

## 7.1. `GET /api/v1/friends`

Danh sách bạn bè.

**Auth**: ✅ · **Thành công**: `200 OK`

### Response 200

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "user": { "id": "...", "username": "bob_h", "display_name": "Bob Ho", "avatar_url": null, "bio": null },
        "friends_since": "2026-09-05T08:00:00.000Z"
      }
    ],
    "page": 0, "size": 20, "total_elements": 1, "total_pages": 1, "has_next": false
  },
  "error": null,
  "timestamp": "2026-09-05T08:00:00.000Z"
}
```

`user` trong mỗi phần tử luôn là **người kia**, không phải chính bạn — bạn không cần tự lọc `id`
của mình ra khỏi kết quả.

### curl

```bash
curl -s http://localhost:8080/api/v1/friends -H "Authorization: Bearer $ALICE_TOKEN"
```

---

## 7.2. `DELETE /api/v1/friends/{id}`

Hủy kết bạn.

**Auth**: ✅ · **Thành công**: `200 OK`

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "2026-09-05T08:00:00.000Z" }
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 404 | `NOT_FRIENDS` | 2 người chưa từng (hoặc không còn) là bạn |

Khác `unblock`, endpoint này **không idempotent** — gọi lại lần 2 sau khi đã hủy thành công sẽ nhận
`404 NOT_FRIENDS` thay vì `200`.

### curl

```bash
curl -s -X DELETE http://localhost:8080/api/v1/friends/906ff5e0-76da-4af5-9265-c53888e70ae8 \
  -H "Authorization: Bearer $ALICE_TOKEN"
```

---

## 8. BẢNG TỔNG HỢP MÃ LỖI

| `error.code` | HTTP | Endpoint có thể gặp | Message mặc định (VI) |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | tất cả | Dữ liệu không hợp lệ (kèm chi tiết field trong `message`) |
| `CANNOT_FRIEND_SELF` | 400 | `POST /friend-requests` | Không thể gửi lời mời kết bạn cho chính mình |
| `CANNOT_BLOCK_SELF` | 400 | `POST /users/{id}/block` | Không thể tự chặn chính mình |
| `UNAUTHORIZED` | 401 | tất cả (thiếu/sai token) | Chưa xác thực |
| `USER_BLOCKED` | 403 | `POST /friend-requests`, `PUT .../accept` | Không thể thực hiện thao tác với người dùng này |
| `USER_NOT_FOUND` | 404 | `PUT/GET /users/{id}`, `POST /friend-requests`, `POST /users/{id}/block` | Không tìm thấy người dùng |
| `FRIEND_REQUEST_NOT_FOUND` | 404 | accept/reject/cancel | Không tìm thấy lời mời kết bạn |
| `NOT_FRIENDS` | 404 | `DELETE /friends/{id}` | Hai người chưa phải là bạn bè |
| `ALREADY_FRIENDS` | 409 | `POST /friend-requests` | Hai người đã là bạn bè |
| `FRIEND_REQUEST_ALREADY_SENT` | 409 | `POST /friend-requests` | Đã gửi lời mời kết bạn, đang chờ phản hồi |
| `FRIEND_REQUEST_NOT_PENDING` | 409 | accept/reject/cancel | Lời mời này đã được xử lý trước đó |
| `ALREADY_BLOCKED` | 409 | `POST /users/{id}/block` | Bạn đã chặn người dùng này |
| `INTERNAL_ERROR` | 500 | bất kỳ (lưới cuối) | Đã có lỗi xảy ra, vui lòng thử lại sau |

---

## 9. KỊCH BẢN TEST END-TO-END

### 9.1. Luồng chính — kết bạn, xem danh sách, hủy

```
1. POST /auth/register + verify + login  → ALICE_TOKEN (xem 07_API_REFERENCE_AUTH.md)
2. (lặp lại bước 1 cho Bob)                → BOB_TOKEN, BOB_ID
3. GET  /users/search?q=bob   (Alice)     → thấy Bob, relationship = NONE
4. POST /friend-requests {receiver_id: BOB_ID}  (Alice) → 201, status = PENDING, lấy REQUEST_ID
5. GET  /friend-requests/received   (Bob)  → thấy lời mời từ Alice
6. PUT  /friend-requests/{REQUEST_ID}/accept  (Bob) → 200, status = ACCEPTED
7. GET  /friends   (Alice)  → thấy Bob
8. GET  /friends   (Bob)    → thấy Alice
9. DELETE /friends/{ALICE_ID}   (Bob)  → 200, hủy kết bạn
10. GET /friends   (Alice)  → rỗng
```

### 9.2. Lời mời chéo tự động thành bạn

```
1. Alice: POST /friend-requests {receiver_id: BOB_ID}   → 201, status = PENDING
2. Bob:   POST /friend-requests {receiver_id: ALICE_ID} → 201, NHƯNG status = ACCEPTED ngay lập tức
3. GET /friends (cả 2 phía) → đã là bạn, KHÔNG có 2 request PENDING nào tồn đọng
```

### 9.3. Double-click accept — không tạo dữ liệu trùng

```
1. Alice gửi lời mời → REQUEST_ID
2. Bob: PUT /friend-requests/{REQUEST_ID}/accept → 200 (lần 1)
3. Bob: PUT /friend-requests/{REQUEST_ID}/accept → 409 FRIEND_REQUEST_NOT_PENDING (lần 2)
4. GET /friends (Alice) → total_elements = 1, KHÔNG phải 2
```

### 9.4. Chặn cắt đứt mọi tương tác, không tiết lộ ai chặn ai

```
1. Alice và Bob đã là bạn (xem 9.1)
2. Alice: POST /users/{BOB_ID}/block  → 200
3. GET /friends (cả 2 phía) → rỗng (friendship bị hủy tự động)
4. Bob: POST /friend-requests {receiver_id: ALICE_ID} → 403 USER_BLOCKED
   (Bob KHÔNG có cách nào qua HTTP để biết mình bị Alice chặn hay Alice bị Bob chặn — cùng 1 lỗi)
5. GET /users/search?q=<username của Bob>  (gọi bởi Alice) → Bob VẪN hiện (Alice chặn Bob, không phải ngược lại)
6. GET /users/search?q=<username của Alice> (gọi bởi Bob)  → Alice KHÔNG hiện (Bob bị Alice chặn)
```

### 9.5. Wildcard injection bị chặn

```
GET /users/search?q=%   → trả về KẾT QUẢ RỖNG (không phải toàn bộ danh sách user)
GET /users/search?q=_   → tương tự, tìm đúng ký tự "_" theo nghĩa đen
```

### 9.6. Actor sai bị từ chối như thể request không tồn tại

```
Alice gửi lời mời cho Bob → REQUEST_ID
Alice tự gọi PUT /friend-requests/{REQUEST_ID}/accept  → 404 FRIEND_REQUEST_NOT_FOUND (không phải 403)
Eve (không liên quan) gọi PUT .../accept                → cũng 404, giống hệt trường hợp trên
```

---

## 10. POSTMAN / CURL COLLECTION

### 10.1. Biến môi trường gợi ý (Postman)

| Variable | Giá trị mẫu |
|---|---|
| `base_url` | `http://localhost:8080` |
| `alice_token`, `bob_token` | (set từ response `/auth/login` — xem collection Auth) |
| `bob_id` | (set từ response `GET /users/search` hoặc `GET /users/me` khi đăng nhập là Bob) |

### 10.2. Toàn bộ curl — copy chạy tuần tự (giả định đã có `$ALICE_TOKEN`, `$BOB_TOKEN`, `$BOB_ID`)

```bash
BASE=http://localhost:8080/api/v1

# 1. Alice tìm Bob
curl -s "$BASE/users/search?q=bob" -H "Authorization: Bearer $ALICE_TOKEN" | jq

# 2. Alice gửi lời mời kết bạn, lưu request id
RESP=$(curl -s -X POST $BASE/friend-requests \
  -H "Authorization: Bearer $ALICE_TOKEN" -H "Content-Type: application/json" \
  -d "{\"receiver_id\":\"$BOB_ID\"}")
echo $RESP | jq
REQUEST_ID=$(echo $RESP | jq -r '.data.id')

# 3. Bob xem lời mời đến, rồi chấp nhận
curl -s $BASE/friend-requests/received -H "Authorization: Bearer $BOB_TOKEN" | jq
curl -s -X PUT $BASE/friend-requests/$REQUEST_ID/accept -H "Authorization: Bearer $BOB_TOKEN" | jq

# 4. Cả 2 xem danh sách bạn bè
curl -s $BASE/friends -H "Authorization: Bearer $ALICE_TOKEN" | jq
curl -s $BASE/friends -H "Authorization: Bearer $BOB_TOKEN" | jq

# 5. Alice chặn Bob
curl -s -X POST $BASE/users/$BOB_ID/block -H "Authorization: Bearer $ALICE_TOKEN" | jq

# 6. Xác nhận bạn bè đã bị hủy
curl -s $BASE/friends -H "Authorization: Bearer $ALICE_TOKEN" | jq   # total_elements: 0

# 7. Bob thử gửi lại lời mời -> bị từ chối
ALICE_ID=$(curl -s $BASE/users/me -H "Authorization: Bearer $ALICE_TOKEN" | jq -r '.data.id')
curl -s -X POST $BASE/friend-requests \
  -H "Authorization: Bearer $BOB_TOKEN" -H "Content-Type: application/json" \
  -d "{\"receiver_id\":\"$ALICE_ID\"}" | jq   # error.code: USER_BLOCKED

# 8. Alice bỏ chặn
curl -s -X DELETE $BASE/users/$BOB_ID/block -H "Authorization: Bearer $ALICE_TOKEN" | jq
```

> Cần `jq` để format JSON đẹp (`choco install jq` trên Windows, hoặc bỏ `| jq` nếu không có).

---

## PHỤ LỤC — Sơ đồ trạng thái lời mời kết bạn

```
PENDING --[accept(), chỉ receiver]--> ACCEPTED  (sinh ra 1 Friendship)
PENDING --[reject(), chỉ receiver]--> REJECTED  (giữ lại, gửi lại được sau này)
PENDING --[cancel(), chỉ sender]-->   CANCELLED (giữ lại, gửi lại được sau này)

Gọi accept/reject/cancel khi status KHÔNG còn PENDING → 409 FRIEND_REQUEST_NOT_PENDING
Gọi accept/reject bởi người không phải receiver, hoặc cancel bởi người không phải sender
  → 404 FRIEND_REQUEST_NOT_FOUND (không phân biệt với "request không tồn tại")
```

Xem `08_PHASE2_USER_FRIEND_REPORT.md` §5–§8 để hiểu **vì sao** thiết kế theo cách này (race condition,
DTO tách theo quyền xem, chống wildcard injection, v.v.).
