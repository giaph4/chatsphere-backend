# API REFERENCE — CHAT MODULE (PHASE 3, REST — CHƯA REAL-TIME)

**Base URL**: `http://localhost:8080` (dev) · **Prefix**: `/api/v1/conversations`, `/api/v1/messages`
**Swagger UI**: `/swagger-ui.html` · **OpenAPI JSON**: `/v3/api-docs`

Tài liệu này mô tả đầy đủ 10 endpoint của module Chat: request/response schema, validation, mã lỗi,
ví dụ `curl`, và các ca biên cần lưu ý. Đi kèm `10_PHASE3_CHAT_REPORT.md` (kiến trúc, luồng, lý
thuyết) và `07_API_REFERENCE_AUTH.md` (đăng ký/đăng nhập để lấy `access_token` dùng ở đây).

> **Chưa real-time**: người nhận tin nhắn phải tự gọi lại `GET .../messages` để thấy tin mới — đây là
> hành vi **mong đợi** ở Phase 3 (đúng như `03_CODE_ROADMAP.md` đặt vấn đề). Phase 4 sẽ thêm kênh
> WebSocket/STOMP gọi lại đúng các method service này rồi broadcast, không đổi hợp đồng REST ở đây.

---

## MỤC LỤC

1. [Quy ước chung](#1-quy-ước-chung)
2. [Xác thực](#2-xác-thực)
3. Hội thoại
   - `GET /conversations` — [Danh sách hội thoại của tôi](#31-get-apiv1conversations)
   - `POST /conversations/direct` — [Tạo/lấy hội thoại 1-1](#32-post-apiv1conversationsdirect)
   - `POST /conversations/group` — [Tạo nhóm](#33-post-apiv1conversationsgroup)
   - `PUT /conversations/{id}` — [Đổi tên/ảnh nhóm](#34-put-apiv1conversationsid)
   - `POST /conversations/{id}/members` — [Thêm thành viên](#35-post-apiv1conversationsidmembers)
   - `DELETE /conversations/{id}/members/{userId}` — [Xóa thành viên](#36-delete-apiv1conversationsidmembersuserid)
   - `POST /conversations/{id}/leave` — [Rời nhóm](#37-post-apiv1conversationsidleave)
4. Tin nhắn
   - `POST /conversations/{id}/messages` — [Gửi tin nhắn](#41-post-apiv1conversationsidmessages)
   - `GET /conversations/{id}/messages` — [Lấy lịch sử (cursor)](#42-get-apiv1conversationsidmessages)
   - `PUT /messages/{id}/recall` — [Thu hồi tin nhắn](#43-put-apiv1messagesidrecall)
5. [Bảng tổng hợp mã lỗi](#5-bảng-tổng-hợp-mã-lỗi)
6. [Kịch bản test end-to-end](#6-kịch-bản-test-end-to-end)
7. [Postman](#7-postman)

---

## 1. QUY ƯỚC CHUNG

Kế thừa toàn bộ quy ước của Auth/User module (`07_API_REFERENCE_AUTH.md` §1): phong bì `ApiResponse`,
JSON **snake_case**, `Content-Type: application/json`, thời gian ISO-8601 UTC.

### 1.1. Danh sách hội thoại — offset pagination (`page`/`size`)

```
GET /api/v1/conversations?page=0&size=20
```

Giống `09_API_REFERENCE_USER_FRIEND.md` §1.2 — bọc trong `PageResponse<T>` (`items`, `page`, `size`,
`total_elements`, `total_pages`, `has_next`). Danh sách hội thoại của 1 user hiếm khi vượt vài trăm
dòng nên offset pagination là đủ, không cần cursor.

### 1.2. Lịch sử tin nhắn — **cursor pagination**, khác hẳn mọi danh sách khác trong dự án

```
GET /api/v1/conversations/{id}/messages?cursor=<messageId>&limit=30
```

```jsonc
{
  "success": true,
  "data": {
    "items": [ /* mảng MessageResponse, MỚI NHẤT TRƯỚC */ ],
    "next_cursor": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
    "has_next": true
  },
  "error": null,
  "timestamp": "2026-09-05T08:00:00.000Z"
}
```

| Field | Ý nghĩa |
|---|---|
| `cursor` (query, optional) | ID tin nhắn cuối trang **trước** — bỏ trống ở lần gọi đầu tiên |
| `limit` (query, optional) | Mặc định 30, tối đa 100 |
| `next_cursor` | ID để truyền vào `cursor` của lần gọi kế tiếp; `null` nếu đã hết |
| `has_next` | Còn trang sau hay không |

**Không có `total_elements`/`total_pages`** — khác `PageResponse`. `COUNT(*)` trên bảng `messages`
(bảng lớn nhất, tăng trưởng nhanh nhất hệ thống) mỗi lần lấy lịch sử là quá đắt và vô nghĩa với UI
dạng cuộn vô hạn — xem `10_PHASE3_CHAT_REPORT.md` §6.1/§10.1 để biết lý do kỹ thuật đầy đủ.

### 1.3. Vai trò participant chỉ có ý nghĩa với GROUP

`ConversationParticipantResponse.role` (`ADMIN`/`MEMBER`) luôn tồn tại kể cả trên conversation
`DIRECT`, nhưng **không mang ý nghĩa nghiệp vụ** ở đó — mọi thao tác yêu cầu quyền ADMIN
(`PUT /conversations/{id}`, `POST/DELETE .../members`) chỉ áp dụng cho `GROUP` (trả
`NOT_A_GROUP_CONVERSATION` nếu gọi trên `DIRECT`).

---

## 2. XÁC THỰC

**Toàn bộ 10 endpoint của module này đều yêu cầu đăng nhập.** Lấy `access_token` từ
`POST /api/v1/auth/login` (xem `07_API_REFERENCE_AUTH.md` §3.3).

```
Authorization: Bearer <access_token>
```

Thiếu header hoặc token sai/hết hạn → **401 `UNAUTHORIZED`**. `currentUserId` luôn lấy từ token đã
verify chữ ký (`@AuthenticationPrincipal UUID`), không bao giờ từ field trong body/query.

---

## 3.1. `GET /api/v1/conversations`

Danh sách hội thoại của tôi, **mới nhất trước** (`updated_at DESC`), kèm `last_message`/
`unread_count`/`participants` rút gọn.

**Auth**: ✅ · **Thành công**: `200 OK`

### Query params

| Param | Mặc định | Ghi chú |
|---|---|---|
| `page` | `0` | 0-indexed |
| `size` | `20` | |

### Response 200

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "510e4558-4f99-4604-be73-8f6e1b223356",
        "type": "GROUP",
        "name": "Study Group",
        "avatar_url": null,
        "last_message": {
          "id": "8dfa4e3d-d781-4384-859d-e1f528745fb2",
          "conversation_id": "510e4558-4f99-4604-be73-8f6e1b223356",
          "sender": { "id": "...", "username": "alice_w", "display_name": "Alice W.", "avatar_url": null, "bio": null },
          "type": "TEXT",
          "content": "Hello everyone",
          "reply_to_message_id": null,
          "forwarded_from_message_id": null,
          "status": "SENT",
          "edited": false,
          "created_at": "2026-09-05T08:00:00.000Z"
        },
        "unread_count": 2,
        "participants": [
          { "user": { "id": "...", "username": "alice_w", "display_name": "Alice W.", "avatar_url": null, "bio": null }, "role": "ADMIN" },
          { "user": { "id": "...", "username": "bob_h", "display_name": "Bob Ho", "avatar_url": null, "bio": null }, "role": "MEMBER" }
        ],
        "updated_at": "2026-09-05T08:00:00.000Z"
      }
    ],
    "page": 0, "size": 20, "total_elements": 1, "total_pages": 1, "has_next": false
  },
  "error": null,
  "timestamp": "2026-09-05T08:00:00.000Z"
}
```

`last_message` là `null` cho conversation vừa tạo, chưa có tin nhắn nào. `unread_count` tính từ
`lastReadMessage` của **chính bạn** trong conversation đó — Phase 3 chưa có endpoint đánh dấu đã đọc
(`chat.markRead` là kênh WebSocket của Phase 4), nên trong phạm vi Phase này, `unread_count` sẽ luôn
bằng tổng số tin nhắn người khác đã gửi kể từ khi bạn tham gia.

### curl

```bash
curl -s "http://localhost:8080/api/v1/conversations?page=0&size=20" -H "Authorization: Bearer $ACCESS"
```

---

## 3.2. `POST /api/v1/conversations/direct`

Tạo hội thoại 1-1 với 1 người, hoặc **trả lại** hội thoại đã có nếu 2 người đã từng chat — không bao
giờ tạo trùng.

**Auth**: ✅ · **Thành công**: `200 OK` (cả khi tạo mới lẫn khi trả lại conversation cũ)

### Request body

| Field | Kiểu | Bắt buộc |
|---|---|---|
| `user_id` | UUID | ✅ |

```json
{ "user_id": "906ff5e0-76da-4af5-9265-c53888e70ae8" }
```

### Response 200

```json
{
  "success": true,
  "data": {
    "id": "1a2b3c4d-0000-0000-0000-000000000000",
    "type": "DIRECT",
    "name": null,
    "avatar_url": null,
    "last_message": null,
    "unread_count": 0,
    "participants": [
      { "user": { "id": "...", "username": "alice_w", "display_name": "Alice W.", "avatar_url": null, "bio": null }, "role": "MEMBER" },
      { "user": { "id": "906ff5e0-...", "username": "bob_h", "display_name": "Bob Ho", "avatar_url": null, "bio": null }, "role": "MEMBER" }
    ],
    "updated_at": "2026-09-05T08:00:00.000Z"
  },
  "error": null,
  "timestamp": "2026-09-05T08:00:00.000Z"
}
```

`role` luôn là `MEMBER` cho cả 2 người ở conversation `DIRECT` — không có khái niệm admin ở đây (§1.3).

### ⚠️ Gọi lại nhiều lần — idempotent theo cặp user, không theo thứ tự gọi

`A → POST {user_id: B}` và `B → POST {user_id: A}` đều trả về **cùng 1 `id`** conversation, dù ai gọi
trước. Đây là hành vi cố ý — dùng để tránh phải tạo endpoint `GET .../direct?userId=` riêng.

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu/sai định dạng `user_id` |
| 400 | `CANNOT_FRIEND_SELF` | `user_id` là chính bạn (dùng chung mã lỗi với Phase 2 — ngữ nghĩa "không thể tự tương tác với chính mình") |
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 403 | `USER_BLOCKED` | Chỉ áp dụng khi **tạo mới** — nếu 2 người đã có conversation từ trước, vẫn trả về được dù giờ đã chặn nhau (xem `10_PHASE3_CHAT_REPORT.md` §5.1) |
| 404 | `USER_NOT_FOUND` | `user_id` không tồn tại hoặc đã xóa mềm |

### curl

```bash
curl -s -X POST http://localhost:8080/api/v1/conversations/direct \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d '{"user_id":"906ff5e0-76da-4af5-9265-c53888e70ae8"}'
```

---

## 3.3. `POST /api/v1/conversations/group`

Tạo nhóm chat. **Người gọi API tự động là `ADMIN`** — không nằm trong `member_ids`.

**Auth**: ✅ · **Thành công**: `201 Created`

### Request body

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `name` | string | ✅ | không rỗng, tối đa 100 ký tự |
| `member_ids` | UUID[] | ✅ | không rỗng (ít nhất 1 thành viên khác ngoài người tạo) |

```json
{ "name": "Study Group", "member_ids": ["906ff5e0-76da-4af5-9265-c53888e70ae8"] }
```

Nếu `member_ids` vô tình chứa chính `user_id` của người gọi, hệ thống **tự loại bỏ** (dedupe) — không
tạo 2 dòng participant cho cùng 1 người.

### Response 201

Giống schema `ConversationResponse` ở [§3.2](#32-post-apiv1conversationsdirect), với `type: "GROUP"`,
`name` là tên đã đặt, `participants` gồm người tạo (`role: "ADMIN"`) + từng thành viên (`role: "MEMBER"`).

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `name` rỗng/quá dài, `member_ids` rỗng |
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 404 | `USER_NOT_FOUND` | Một `id` trong `member_ids` không tồn tại hoặc đã xóa mềm |

### curl

```bash
curl -s -X POST http://localhost:8080/api/v1/conversations/group \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d '{"name":"Study Group","member_ids":["906ff5e0-76da-4af5-9265-c53888e70ae8"]}'
```

---

## 3.4. `PUT /api/v1/conversations/{id}`

Đổi tên/ảnh nhóm. **PUT thay thế toàn bộ** — chỉ `ADMIN`, chỉ áp dụng cho `GROUP`.

**Auth**: ✅ · **Thành công**: `200 OK`

### Request body

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `name` | string | ✅ | không rỗng, tối đa 100 ký tự |
| `avatar_url` | string \| `null` | ❌ | tối đa 500 ký tự; `null` = xóa ảnh (quay về mặc định) |

```json
{ "name": "Study Group (renamed)", "avatar_url": null }
```

### Response 200

Giống schema `ConversationResponse` (§3.2), phản ánh giá trị sau khi cập nhật.

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `name` rỗng/quá dài |
| 400 | `NOT_A_GROUP_CONVERSATION` | `{id}` là conversation `DIRECT` |
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 403 | `NOT_CONVERSATION_MEMBER` | Bạn không phải thành viên active của nhóm |
| 403 | `GROUP_ADMIN_REQUIRED` | Bạn là thành viên nhưng không phải `ADMIN` |
| 404 | `CONVERSATION_NOT_FOUND` | `{id}` không tồn tại |

### curl

```bash
curl -s -X PUT http://localhost:8080/api/v1/conversations/510e4558-4f99-4604-be73-8f6e1b223356 \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d '{"name":"Study Group (renamed)","avatar_url":null}'
```

---

## 3.5. `POST /api/v1/conversations/{id}/members`

Thêm thành viên vào nhóm. Chỉ `ADMIN`.

**Auth**: ✅ · **Thành công**: `201 Created`

### Request body

| Field | Kiểu | Bắt buộc |
|---|---|---|
| `user_id` | UUID | ✅ |

```json
{ "user_id": "eab0f3c0-0000-0000-0000-000000000000" }
```

### Response 201

```json
{ "success": true, "data": null, "error": null, "timestamp": "2026-09-05T08:00:00.000Z" }
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu/sai định dạng `user_id` |
| 400 | `NOT_A_GROUP_CONVERSATION` | `{id}` là `DIRECT` |
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 403 | `NOT_CONVERSATION_MEMBER` / `GROUP_ADMIN_REQUIRED` | Không phải thành viên / không phải ADMIN |
| 404 | `CONVERSATION_NOT_FOUND` | `{id}` không tồn tại |
| 404 | `USER_NOT_FOUND` | `user_id` không tồn tại hoặc đã xóa mềm |
| 409 | `ALREADY_CONVERSATION_MEMBER` | Người này đã là thành viên **active** của nhóm |

Người từng rời nhóm trước đó (soft-leave, `left_at` khác null) **thêm lại được bình thường** — không
bị chặn bởi lỗi 409, vì partial unique index chỉ áp cho dòng active (xem `10_PHASE3_CHAT_REPORT.md` §3.4).

### curl

```bash
curl -s -X POST http://localhost:8080/api/v1/conversations/510e4558-4f99-4604-be73-8f6e1b223356/members \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d '{"user_id":"eab0f3c0-0000-0000-0000-000000000000"}'
```

---

## 3.6. `DELETE /api/v1/conversations/{id}/members/{userId}`

Xóa thành viên khỏi nhóm (soft leave — set `left_at`, không xóa dòng). Chỉ `ADMIN`.

**Auth**: ✅ · **Thành công**: `200 OK`

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "2026-09-05T08:00:00.000Z" }
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `NOT_A_GROUP_CONVERSATION` | `{id}` là `DIRECT` |
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 403 | `NOT_CONVERSATION_MEMBER` / `GROUP_ADMIN_REQUIRED` | Người gọi không phải thành viên / không phải ADMIN |
| 404 | `CONVERSATION_NOT_FOUND` | `{id}` không tồn tại |
| 403 | `NOT_CONVERSATION_MEMBER` | `{userId}` không phải thành viên active của nhóm (mã lỗi dùng chung cho cả 2 phía) |

> **Lưu ý vận hành**: nếu `ADMIN` tự xóa chính mình qua endpoint này, hệ thống **không** tự động
> chuyển quyền ADMIN cho ai khác (logic chuyển quyền chỉ chạy trong `POST .../leave` — §3.7). Admin
> muốn rời nhóm nên dùng `leave`, không dùng endpoint này cho chính mình.

### curl

```bash
curl -s -X DELETE http://localhost:8080/api/v1/conversations/510e4558.../members/eab0f3c0... \
  -H "Authorization: Bearer $ACCESS"
```

---

## 3.7. `POST /api/v1/conversations/{id}/leave`

Rời nhóm. Nếu người rời là **`ADMIN` cuối cùng** còn lại và nhóm vẫn còn thành viên khác, hệ thống tự
động chuyển quyền `ADMIN` cho người **tham gia sớm nhất**.

**Auth**: ✅ · **Thành công**: `200 OK`

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "2026-09-05T08:00:00.000Z" }
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `NOT_A_GROUP_CONVERSATION` | `{id}` là `DIRECT` (không có khái niệm "rời" hội thoại 1-1) |
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 403 | `NOT_CONVERSATION_MEMBER` | Bạn không phải (hoặc không còn là) thành viên active |
| 404 | `CONVERSATION_NOT_FOUND` | `{id}` không tồn tại |

### curl

```bash
curl -s -X POST http://localhost:8080/api/v1/conversations/510e4558-4f99-4604-be73-8f6e1b223356/leave \
  -H "Authorization: Bearer $ACCESS"
```

---

## 4.1. `POST /api/v1/conversations/{id}/messages`

Gửi tin nhắn vào 1 cuộc trò chuyện. **Chưa real-time** — người nhận phải tự `GET` lại (§1.2 / §4.2).

**Auth**: ✅ · **Thành công**: `201 Created`

### Request body

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `type` | `"TEXT"` \| `"IMAGE"` \| `"FILE"` \| `"VOICE"` \| `"SYSTEM"` | ✅ | Phase 3 mới thật sự hỗ trợ `TEXT` — `MessageAttachment` cho các type còn lại thêm ở Phase 5 |
| `content` | string | ✅ | không rỗng, tối đa 5000 ký tự |
| `reply_to_message_id` | UUID \| `null` | ❌ | phải thuộc **cùng** conversation |

```json
{ "type": "TEXT", "content": "Hello Bob!", "reply_to_message_id": null }
```

### Response 201

```json
{
  "success": true,
  "data": {
    "id": "8dfa4e3d-d781-4384-859d-e1f528745fb2",
    "conversation_id": "1a2b3c4d-0000-0000-0000-000000000000",
    "sender": { "id": "...", "username": "alice_w", "display_name": "Alice W.", "avatar_url": null, "bio": null },
    "type": "TEXT",
    "content": "Hello Bob!",
    "reply_to_message_id": null,
    "forwarded_from_message_id": null,
    "status": "SENT",
    "edited": false,
    "created_at": "2026-09-05T08:00:00.000Z"
  },
  "error": null,
  "timestamp": "2026-09-05T08:00:00.000Z"
}
```

Gửi thành công cũng cập nhật `conversations.last_message`/`updated_at` — conversation này sẽ nhảy
lên đầu danh sách ở [`GET /conversations`](#31-get-apiv1conversations) của **mọi** thành viên.

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `content` rỗng/quá 5000 ký tự, thiếu `type` |
| 400 | `MESSAGE_NOT_IN_CONVERSATION` | `reply_to_message_id` trỏ tới tin nhắn thuộc conversation **khác** |
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 403 | `NOT_CONVERSATION_MEMBER` | Bạn không phải thành viên active của `{id}` |
| 403 | `USER_BLOCKED` | Chỉ với conversation `DIRECT`: có quan hệ chặn giữa 2 người — **GROUP không áp dụng** (§1.3 báo cáo Phase 3 §5.3) |
| 404 | `CONVERSATION_NOT_FOUND` | `{id}` không tồn tại |
| 404 | `MESSAGE_NOT_FOUND` | `reply_to_message_id` không tồn tại |

### curl

```bash
curl -s -X POST http://localhost:8080/api/v1/conversations/1a2b3c4d.../messages \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d '{"type":"TEXT","content":"Hello Bob!"}'
```

---

## 4.2. `GET /api/v1/conversations/{id}/messages`

Lấy lịch sử tin nhắn — **cursor-based pagination**, mới nhất trước. Xem cơ chế đầy đủ ở §1.2.

**Auth**: ✅ · **Thành công**: `200 OK`

### Query params

| Param | Bắt buộc | Mặc định | Ghi chú |
|---|---|---|---|
| `cursor` | ❌ | (trang mới nhất) | ID tin nhắn cuối trang trước |
| `limit` | ❌ | `30` | tối đa `100`, tự động kẹp (clamp) nếu vượt |

```
GET /api/v1/conversations/1a2b3c4d.../messages?limit=20
GET /api/v1/conversations/1a2b3c4d.../messages?limit=20&cursor=8dfa4e3d-d781-4384-859d-e1f528745fb2
```

### Response 200

Xem ví dụ đầy đủ ở §1.2. `items[]` là mảng `MessageResponse` (cùng schema với §4.1), mới nhất trước.

Tin nhắn đã bị **thu hồi** (`status: "RECALLED"`) vẫn xuất hiện trong danh sách (để client hiển thị
placeholder "Tin nhắn đã được thu hồi"), nhưng `content` sẽ là `null` (field vắng mặt trong JSON do
`default-property-inclusion: non_null`) — xem §4.3.

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 403 | `NOT_CONVERSATION_MEMBER` | Bạn không phải thành viên active của `{id}` |
| 404 | `CONVERSATION_NOT_FOUND` | `{id}` không tồn tại |
| 404 | `MESSAGE_NOT_FOUND` | `cursor` không tồn tại |
| 400 | `MESSAGE_NOT_IN_CONVERSATION` | `cursor` là ID tin nhắn thuộc conversation **khác** |

### curl

```bash
curl -s "http://localhost:8080/api/v1/conversations/1a2b3c4d.../messages?limit=20" \
  -H "Authorization: Bearer $ACCESS"
```

---

## 4.3. `PUT /api/v1/messages/{id}/recall`

Thu hồi tin nhắn. **Chỉ người gửi**, **trong vòng 5 phút** kể từ `created_at`.

**Auth**: ✅ · **Thành công**: `200 OK`

### Response 200

```json
{
  "success": true,
  "data": {
    "id": "8dfa4e3d-d781-4384-859d-e1f528745fb2",
    "conversation_id": "1a2b3c4d-0000-0000-0000-000000000000",
    "sender": { "id": "...", "username": "alice_w", "display_name": "Alice W.", "avatar_url": null, "bio": null },
    "type": "TEXT",
    "reply_to_message_id": null,
    "forwarded_from_message_id": null,
    "status": "RECALLED",
    "edited": false,
    "created_at": "2026-09-05T08:00:00.000Z"
  },
  "error": null,
  "timestamp": "2026-09-05T08:00:00.000Z"
}
```

> **Chú ý**: `content` **không xuất hiện** trong JSON (không phải `"content": null`) — cấu hình
> Jackson toàn cục `default-property-inclusion: non_null` loại field có giá trị `null` khỏi output.
> Nội dung bị xóa **thật** trong DB, không chỉ đổi cờ `status` (xem `10_PHASE3_CHAT_REPORT.md` §6.3).

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 401 | `UNAUTHORIZED` | Thiếu/sai token |
| 403 | `MESSAGE_RECALL_FORBIDDEN` | Bạn không phải người gửi tin nhắn này |
| 404 | `MESSAGE_NOT_FOUND` | `{id}` không tồn tại hoặc đã bị xóa (soft-delete) |
| 409 | `MESSAGE_ALREADY_RECALLED` | Tin nhắn đã được thu hồi từ trước |
| 409 | `MESSAGE_RECALL_WINDOW_EXPIRED` | Đã quá 5 phút kể từ lúc gửi |

### curl

```bash
curl -s -X PUT http://localhost:8080/api/v1/messages/8dfa4e3d-d781-4384-859d-e1f528745fb2/recall \
  -H "Authorization: Bearer $ACCESS"
```

---

## 5. BẢNG TỔNG HỢP MÃ LỖI

| `error.code` | HTTP | Endpoint có thể gặp | Message mặc định (VI) |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | tất cả có body | Dữ liệu không hợp lệ |
| `CANNOT_FRIEND_SELF` | 400 | `POST /conversations/direct` | Không thể tự tạo hội thoại với chính mình |
| `NOT_A_GROUP_CONVERSATION` | 400 | update/members/leave | Thao tác này chỉ áp dụng cho nhóm chat |
| `MESSAGE_NOT_IN_CONVERSATION` | 400 | send (reply), get messages (cursor) | Tin nhắn được reply/cursor không thuộc cuộc trò chuyện này |
| `UNAUTHORIZED` | 401 | tất cả (thiếu/sai token) | Chưa xác thực |
| `NOT_CONVERSATION_MEMBER` | 403 | hầu hết endpoint | Bạn không phải thành viên của cuộc trò chuyện này |
| `GROUP_ADMIN_REQUIRED` | 403 | update/add/remove member | Chỉ trưởng nhóm mới được thực hiện thao tác này |
| `USER_BLOCKED` | 403 | tạo/gửi tin `DIRECT` | Không thể thực hiện thao tác với người dùng này |
| `MESSAGE_RECALL_FORBIDDEN` | 403 | `PUT .../recall` | Chỉ người gửi mới được thu hồi tin nhắn |
| `CONVERSATION_NOT_FOUND` | 404 | hầu hết endpoint theo `{id}` | Không tìm thấy cuộc trò chuyện |
| `MESSAGE_NOT_FOUND` | 404 | send (reply), get messages (cursor), recall | Không tìm thấy tin nhắn |
| `USER_NOT_FOUND` | 404 | tạo direct/group, add member | Không tìm thấy người dùng |
| `ALREADY_CONVERSATION_MEMBER` | 409 | `POST .../members` | Người này đã ở trong nhóm |
| `MESSAGE_ALREADY_RECALLED` | 409 | `PUT .../recall` | Tin nhắn đã được thu hồi trước đó |
| `MESSAGE_RECALL_WINDOW_EXPIRED` | 409 | `PUT .../recall` | Đã quá thời gian cho phép thu hồi tin nhắn (5 phút) |
| `INTERNAL_ERROR` | 500 | bất kỳ (lưới cuối) | Đã có lỗi xảy ra, vui lòng thử lại sau |

---

## 6. KỊCH BẢN TEST END-TO-END

### 6.1. Luồng chính — group 3 người, gửi tin, phân trang, thu hồi, rời nhóm

Đúng kịch bản "Kiểm tra hoàn thành Phase 3" của roadmap, tự động hóa ở `ChatControllerIntegrationTest`:

```
1. Đăng ký + xác thực + đăng nhập Alice, Bob, Carol
2. POST /conversations/group {name, member_ids:[BOB_ID, CAROL_ID]}  (Alice) → 201, 3 participant
3. POST .../messages  (Alice, Bob, Carol lần lượt)  → 3 tin nhắn
4. GET .../messages?limit=2  (Alice) → 2 tin mới nhất, has_next=true, next_cursor
5. GET .../messages?limit=2&cursor=<next_cursor>  (Alice) → tin còn lại, has_next=false
6. PUT /messages/{carolMsgId}/recall  (Bob) → 403 MESSAGE_RECALL_FORBIDDEN
7. PUT /messages/{carolMsgId}/recall  (Carol) → 200, status=RECALLED, content vắng mặt trong JSON
8. POST .../leave  (Carol) → 200
9. GET /conversations  (Carol) → rỗng
10. GET /conversations  (Alice) → vẫn thấy nhóm, participants còn 2
```

### 6.2. Direct conversation — gọi 2 lần, 2 chiều, vẫn ra cùng 1 id

```
1. Alice: POST /conversations/direct {user_id: BOB_ID}  → conversation X
2. Alice: POST /conversations/direct {user_id: BOB_ID}  (gọi lại) → VẪN là conversation X
3. (Đối xứng) Bob: POST /conversations/direct {user_id: ALICE_ID} → VẪN là conversation X
```

### 6.3. Block chỉ chặn DIRECT, không chặn GROUP

```
1. Alice và Bob cùng ở trong 1 nhóm chat với Carol
2. Alice chặn Bob (POST /users/{BOB_ID}/block — Phase 2)
3. Alice: POST /conversations/direct {user_id: BOB_ID}  → 403 USER_BLOCKED (tạo mới DIRECT bị chặn)
4. Alice: POST /conversations/{nhóm}/messages {...}  → 201 (GROUP vẫn gửi được bình thường)
```

### 6.4. Quyền ADMIN — chỉ ADMIN mới quản lý được nhóm

```
1. Alice tạo nhóm với Bob (Alice = ADMIN, Bob = MEMBER)
2. Bob: POST /conversations/{id}/members {user_id: EVE_ID}  → 403 GROUP_ADMIN_REQUIRED
3. Alice: POST /conversations/{id}/members {user_id: EVE_ID}  → 201 (Alice là ADMIN)
```

### 6.5. Rời nhóm tự động chuyển quyền

```
1. Alice tạo nhóm chỉ với Bob (Alice = ADMIN, Bob = MEMBER)
2. Alice: POST /conversations/{id}/leave  → 200
3. Kiểm tra: Bob giờ có role = ADMIN (tự động, không cần gọi API nào thêm)
```

### 6.6. Thu hồi ngoài cửa sổ 5 phút

```
1. Alice gửi tin nhắn lúc T
2. Tại T + 6 phút: Alice: PUT /messages/{id}/recall  → 409 MESSAGE_RECALL_WINDOW_EXPIRED
```

---

## 7. POSTMAN

Collection **"ChatSphere Backend"** (Team Workspace) đã có sẵn 10 request Phase 3, tiền tố `Chat -`,
nằm ở gốc collection (cùng cấp với folder `Auth`/`User`/`Friend`):

| Request | Ghi chú |
|---|---|
| `Chat - Get My Conversations` | dùng `{{alice_token}}` |
| `Chat - Create Or Get Direct Conversation` | test script tự lưu `conversation_id` |
| `Chat - Create Group` | test script tự lưu `group_conversation_id` |
| `Chat - Update Group Info` | |
| `Chat - Add Member` | dùng lại `{{target_user_id}}` có sẵn từ Phase 2 |
| `Chat - Remove Member` | |
| `Chat - Leave Group` | dùng `{{bob_token}}` (Bob rời nhóm Alice tạo) |
| `Chat - Send Message` | dùng `{{conversation_id}}`, test script tự lưu `message_id` |
| `Chat - Get Messages (cursor pagination)` | dùng `{{conversation_id}}`; tick `cursor` để lấy trang 2 |
| `Chat - Recall Message` | dùng `{{message_id}}` |

**Thứ tự chạy gợi ý** để có đủ dữ liệu chuyền tay qua collection variables (giống cơ chế
`access_token` tự lưu ở Phase 1/2): `Login Alice`/`Login Bob` (folder `Full Flow`) →
`Chat - Create Or Get Direct Conversation` → `Chat - Send Message` (chạy vài lần để có dữ liệu phân
trang) → `Chat - Get Messages` → `Chat - Recall Message` → `Chat - Create Group` →
`Chat - Update Group Info` / `Chat - Add Member` / `Chat - Remove Member` → `Chat - Leave Group`.

File export tĩnh của collection (đồng bộ thủ công với bản trên Postman) nằm ở
`docs/apis-docs/ChatSphere.postman_collection.json`.
