# API REFERENCE — MEDIA & NOTIFICATION (PHASE 5)

**Base URL**: `http://localhost:8080` (dev) · **Prefix**: `/api/v1/media`, `/api/v1/notifications`,
và các endpoint bổ sung trên `/api/v1/messages`, `/api/v1/conversations`
**Swagger UI**: `/swagger-ui.html` · **OpenAPI JSON**: `/v3/api-docs`

Tài liệu này mô tả 12 endpoint của Phase 5: upload media, reaction, forward, xóa phía mình, tắt
thông báo, thông báo trong ứng dụng và Web Push. Đi kèm `14_PHASE5_MEDIA_NOTIFICATION_REPORT.md`
(kiến trúc, lý do thiết kế) và `11_API_REFERENCE_CHAT.md` (module chat gốc — Phase 5 **mở rộng**
`SendMessageRequest`/`MessageResponse` chứ không phá vỡ).

---

## MỤC LỤC

1. [Quy ước chung](#1-quy-ước-chung)
2. [Xác thực](#2-xác-thực)
3. Media
   - `POST /media/upload` — [Tải file lên](#31-post-apiv1mediaupload)
   - [Gửi tin nhắn kèm tệp](#32-gửi-tin-nhắn-kèm-tệp)
4. Tin nhắn (bổ sung Phase 5)
   - `PUT /messages/{id}/reactions` — [Thả cảm xúc](#41-put-apiv1messagesidreactions)
   - `POST /messages/{id}/forward` — [Chuyển tiếp](#42-post-apiv1messagesidforward)
   - `DELETE /messages/{id}/for-me` — [Ẩn phía mình](#43-delete-apiv1messagesidfor-me)
   - `PUT /conversations/{id}/mute` — [Tắt thông báo](#44-put-apiv1conversationsidmute)
5. Thông báo
   - `GET /notifications` — [Danh sách](#51-get-apiv1notifications)
   - `GET /notifications/unread-count` — [Số chưa đọc](#52-get-apiv1notificationsunread-count)
   - `PUT /notifications/{id}/read` — [Đánh dấu 1 cái đã đọc](#53-put-apiv1notificationsidread)
   - `PUT /notifications/read-all` — [Đánh dấu tất cả](#54-put-apiv1notificationsread-all)
6. Web Push
   - `GET /notifications/push/public-key` — [Khóa VAPID](#61-get-apiv1notificationspushpublic-key)
   - `POST /notifications/push/subscribe` — [Đăng ký thiết bị](#62-post-apiv1notificationspushsubscribe)
   - `DELETE /notifications/push/subscribe` — [Hủy đăng ký](#63-delete-apiv1notificationspushsubscribe)
7. [Bảng tổng hợp mã lỗi](#7-bảng-tổng-hợp-mã-lỗi)
8. [Kịch bản test end-to-end](#8-kịch-bản-test-end-to-end)

---

## 1. QUY ƯỚC CHUNG

Kế thừa toàn bộ quy ước của các module trước (`07_API_REFERENCE_AUTH.md` §1): phong bì
`ApiResponse`, `snake_case`, field `null` bị loại khỏi JSON, thời gian ISO-8601 UTC.

### 1.1. Hai field mới trên mọi `MessageResponse`

Phase 5 thêm `attachments` và `reactions` vào **mọi** response chứa tin nhắn — REST lẫn WebSocket.
Đây là thay đổi **cộng thêm**, không phá vỡ hợp đồng Phase 3.

```json
{
  "id": "3f1c...",
  "type": "IMAGE",
  "content": "Xem anh nay",
  "attachments": [
    {
      "id": "7d2a...",
      "file_url": "http://localhost:9000/chatsphere-media/2026/09/06/8c1e....png",
      "file_name": "bien.png",
      "file_type": "image/png",
      "file_size": 204800
    }
  ],
  "reactions": [
    { "emoji": "❤️", "count": 2, "user_ids": ["11ee...", "22ff..."] }
  ],
  "status": "SENT",
  "created_at": "2026-09-06T10:15:30Z"
}
```

> Cả hai **luôn là mảng**, không bao giờ `null` — client duyệt thẳng không cần kiểm tra.
> `thumbnail_url` hiện luôn vắng mặt (chưa sinh thumbnail — xem nợ kỹ thuật §11 của báo cáo).

### 1.2. Reaction được gom sẵn theo emoji

Server **không** trả từng dòng reaction thô mà gom sẵn: `emoji` + `count` + `user_ids`. Giao diện
cần đúng thứ này ("❤️ 3" kèm danh sách khi rê chuột). Thứ tự: nhiều nhất trước, rồi theo chữ cái —
**ổn định giữa các lần gọi** nên danh sách không nhảy lung tung khi hai emoji cùng số lượng.

---

## 2. XÁC THỰC

**Toàn bộ 12 endpoint đều yêu cầu đăng nhập.**

```
Authorization: Bearer <access_token>
```

Thiếu header hoặc token sai/hết hạn → **401 `UNAUTHORIZED`**. `currentUserId` luôn lấy từ token đã
verify chữ ký, không bao giờ từ field trong body/query.

---

## 3. MEDIA

## 3.1. `POST /api/v1/media/upload`

Tải một file lên object storage. **Không** gắn với hội thoại nào — cùng một file có thể gửi lại vào
nhiều hội thoại, và lúc upload người dùng còn chưa chắc sẽ gửi cho ai.

**Content-Type**: `multipart/form-data`

| Part | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `file` | file | ✅ | |
| `category` | enum | ❌ | `IMAGE` \| `VOICE` \| `FILE` — mặc định `FILE` |

### Giới hạn theo `category`

| Category | Tối đa | MIME được phép (allowlist) |
|---|---|---|
| `IMAGE` | 10 MB | `image/jpeg`, `image/png`, `image/gif`, `image/webp` |
| `VOICE` | 10 MB | `audio/mpeg`, `audio/mp4`, `audio/ogg`, `audio/webm`, `audio/wav`, `audio/x-wav` |
| `FILE` | 25 MB | `application/pdf`, `application/zip`, `text/plain`, `text/csv`, Word/Excel (`.doc/.docx/.xls/.xlsx`) |

> ⚠️ **Server KHÔNG tin `Content-Type` bạn gửi, cũng không tin đuôi file.** Kiểu file được xác định
> bằng cách đọc vài byte đầu của nội dung thật (magic byte). Đổi tên `virus.exe` thành `anh.jpg` và
> khai `image/jpeg` vẫn bị chặn với `415 FILE_TYPE_NOT_ALLOWED`.
>
> Hệ quả cần biết khi ráp frontend: một file `.png` bị hỏng header, hoặc một `.docx` được tạo bởi
> công cụ lạ, có thể bị nhận diện khác kỳ vọng. Luôn hiển thị `message` trong lỗi cho người dùng.

### Response 201

```json
{
  "success": true,
  "data": {
    "file_url": "http://localhost:9000/chatsphere-media/2026/09/06/8c1e2f....png",
    "file_name": "anh-cua-toi.png",
    "file_type": "image/png",
    "file_size": 204800,
    "category": "IMAGE"
  },
  "error": null,
  "timestamp": "2026-09-06T10:15:30Z"
}
```

| Field | Ghi chú |
|---|---|
| `file_url` | Tên object là UUID, **không** phải tên gốc — chống ghi đè và path traversal |
| `file_name` | Tên gốc, đã cắt mọi thành phần đường dẫn, để hiển thị cho người nhận |
| `file_type` | MIME **thật** do Tika phát hiện, có thể **khác** thứ bạn gửi lên |

### Lỗi có thể gặp

| HTTP | Mã | Khi nào |
|---|---|---|
| 400 | `FILE_EMPTY` | Không chọn file hoặc file 0 byte |
| 413 | `FILE_TOO_LARGE` | Vượt hạn mức của `category` |
| 415 | `FILE_TYPE_NOT_ALLOWED` | Kiểu thật không nằm trong allowlist của `category` |
| 500 | `FILE_UPLOAD_FAILED` | MinIO không phản hồi |

### curl

```bash
curl -X POST http://localhost:8080/api/v1/media/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@anh-cua-toi.png" \
  -F "category=IMAGE"
```

---

## 3.2. Gửi tin nhắn kèm tệp

Không có endpoint riêng. Dùng lại `POST /api/v1/conversations/{id}/messages` (Phase 3) hoặc
`/app/chat.sendMessage` (Phase 4), thêm mảng `attachments` — **chép nguyên** giá trị từ response
của bước upload.

```json
{
  "type": "IMAGE",
  "attachments": [
    {
      "file_url": "http://localhost:9000/chatsphere-media/2026/09/06/8c1e....png",
      "file_name": "anh-cua-toi.png",
      "file_type": "image/png",
      "file_size": 204800
    }
  ]
}
```

### Quy tắc hợp lệ (Phase 5 nới so với Phase 3)

| Quy tắc | Mã lỗi khi vi phạm |
|---|---|
| Phải có `content` **hoặc** `attachments` | `MESSAGE_CONTENT_REQUIRED` |
| `type` là `IMAGE`/`FILE`/`VOICE` thì **bắt buộc** có `attachments` | `ATTACHMENT_REQUIRED` |
| Tối đa 10 tệp mỗi tin nhắn | `VALIDATION_ERROR` |
| `file_url` phải trỏ vào bucket của hệ thống | `FILE_TYPE_NOT_ALLOWED` |

> **`content` không còn `@NotBlank`**: tin nhắn chỉ có ảnh, không kèm chữ, là hoàn toàn hợp lệ.
>
> **Vì sao chặn `file_url` ngoài hệ thống**: không có chốt này, client gửi một URL bất kỳ trên
> Internet và giao diện sẽ hiển thị nó y như tệp nội bộ đã được kiểm duyệt — trong khi nội dung nằm
> ngoài tầm kiểm soát và có thể đổi bất cứ lúc nào **sau khi** gửi.

**Luồng 2 bước là cố ý**: người dùng chọn ảnh xong là upload chạy nền ngay trong lúc họ còn gõ chú
thích, nên lúc bấm Gửi thì tin bay đi tức thì; gửi lại tin thất bại cũng không phải tải lên lần nữa.

---

## 4. TIN NHẮN — BỔ SUNG PHASE 5

## 4.1. `PUT /api/v1/messages/{id}/reactions`

Thả, đổi, hoặc gỡ cảm xúc (UC-22).

### Request body

```json
{ "emoji": "❤️" }
```

| Field | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `emoji` | string ≤10 ký tự | ✅ | Giới hạn 10 tính theo ký tự Java, không phải "1 emoji" — nhiều emoji hiện đại là chuỗi ghép nhiều code point (màu da, cờ, gia đình) |

### ⚠️ Ba hành vi trong MỘT endpoint (toggle)

| Trạng thái hiện tại | Gửi lên | Kết quả |
|---|---|---|
| Chưa thả gì | `"❤️"` | **Thêm** ❤️ |
| Đang thả `"❤️"` | `"😂"` | **Đổi** sang 😂 (vẫn 1 reaction, không phải 2) |
| Đang thả `"😂"` | `"😂"` | **Gỡ** bỏ |

Mỗi người chỉ có **đúng một** reaction trên mỗi tin nhắn. Đây cũng là lý do không cần endpoint
`DELETE` riêng.

### Response 200

Trả về `MessageResponse` đầy đủ với mảng `reactions` đã cập nhật:

```json
{
  "success": true,
  "data": {
    "id": "3f1c...",
    "reactions": [
      { "emoji": "❤️", "count": 2, "user_ids": ["11ee...", "22ff..."] },
      { "emoji": "😂", "count": 1, "user_ids": ["33aa..."] }
    ]
  }
}
```

### Lỗi có thể gặp

| HTTP | Mã | Khi nào |
|---|---|---|
| 404 | `MESSAGE_NOT_FOUND` | Tin không tồn tại hoặc đã bị xóa |
| 409 | `MESSAGE_ALREADY_RECALLED` | Tin đã bị thu hồi |
| 403 | `NOT_CONVERSATION_MEMBER` | Không phải thành viên hội thoại chứa tin đó |

### curl

```bash
curl -X PUT http://localhost:8080/api/v1/messages/$MSG/reactions \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"emoji":"❤️"}'
```

---

## 4.2. `POST /api/v1/messages/{id}/forward`

Chuyển tiếp tin nhắn sang hội thoại khác (UC-21).

### Request body

```json
{ "target_conversation_id": "9ab2..." }
```

### Response 201

Trả về **tin nhắn MỚI** ở hội thoại đích — `id` khác tin gốc:

```json
{
  "success": true,
  "data": {
    "id": "5e7d...",
    "conversation_id": "9ab2...",
    "content": "Xem anh nay",
    "forwarded_from_message_id": "3f1c...",
    "attachments": [ { "file_url": "...(cùng URL với tin gốc)..." } ]
  }
}
```

> **Nội dung được SAO CHÉP, không trỏ tới tin gốc.** Người gửi gốc thu hồi tin của họ thì bản
> chuyển tiếp **vẫn còn nguyên** — người nhận bản chuyển tiếp chưa từng đồng ý bị xóa nội dung, và
> họ cũng không nhìn thấy hội thoại gốc để hiểu chuyện gì vừa xảy ra. `forwarded_from_message_id`
> chỉ để hiển thị nhãn "Đã chuyển tiếp".
>
> Đính kèm cũng được nhân bản ở tầng metadata nhưng **cùng trỏ tới một file** trên storage — không
> tải lại.

### Quyền

Người chuyển tiếp phải là thành viên của **cả hai** hội thoại: nguồn (để được đọc tin) và đích (để
được gửi). Thiếu vế đầu thì chuyển tiếp trở thành cách đọc trộm hội thoại người khác.

### Lỗi có thể gặp

| HTTP | Mã | Khi nào |
|---|---|---|
| 404 | `MESSAGE_NOT_FOUND` | Tin nguồn không tồn tại |
| 409 | `MESSAGE_ALREADY_RECALLED` | Tin nguồn đã thu hồi |
| 403 | `NOT_CONVERSATION_MEMBER` | Không phải thành viên hội thoại nguồn **hoặc** đích |
| 403 | `USER_BLOCKED` | Hội thoại đích là DIRECT và hai bên đã chặn nhau |

---

## 4.3. `DELETE /api/v1/messages/{id}/for-me`

Ẩn tin nhắn khỏi tầm mắt **chính mình** (UC-28).

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "..." }
```

### ⚠️ Phân biệt với thu hồi — hai khái niệm hoàn toàn khác

| | **Thu hồi** (`PUT /messages/{id}/recall`) | **Ẩn phía mình** (endpoint này) |
|---|---|---|
| Ai thấy thay đổi | **Mọi người** | Chỉ chính mình |
| Ai làm được | Chỉ người gửi | Ai cũng được |
| Giới hạn thời gian | 5 phút | Không |
| Có phát real-time | ✅ | ❌ |

Endpoint này **cố ý không** phát sự kiện WebSocket: đây là thay đổi riêng tư của một người, phát cho
cả hội thoại sẽ tiết lộ chính xác điều họ vừa muốn giấu.

**Idempotent** — gọi nhiều lần không lỗi.

> **Lưu ý khi phân trang**: tin đã ẩn bị lọc **sau** khi lấy trang, nên `GET /messages?limit=30` có
> thể trả về **ít hơn** 30 tin. Đây là hành vi mong đợi. `next_cursor` vẫn đúng.

---

## 4.4. `PUT /api/v1/conversations/{id}/mute`

Tắt thông báo của một hội thoại tới thời điểm chỉ định (UC-27).

### Request body

```json
{ "muted_until": "2026-09-07T00:00:00Z" }
```

| Giá trị | Ý nghĩa |
|---|---|
| Mốc thời gian tương lai | Tắt tới lúc đó, **tự bật lại** khi qua mốc |
| `null` | **Bật lại ngay** |
| Mốc quá khứ | `400 VALIDATION_ERROR` |

> Dùng chung một endpoint cho cả bật và tắt vì đây là hai trạng thái của cùng một cài đặt, không
> phải hai hành động khác nhau. Muốn tắt "vĩnh viễn" thì gửi một mốc rất xa (ví dụ năm 2099) — server
> không cần biết khái niệm đó.
>
> **Mute là cài đặt của từng người** trên hội thoại chung, không ảnh hưởng người khác trong nhóm.
> Nó chỉ chặn **thông báo**; tin nhắn real-time vẫn tới bình thường nếu bạn đang mở hội thoại.

---

## 5. THÔNG BÁO

## 5.1. `GET /api/v1/notifications`

Danh sách thông báo của tôi, mới nhất trước.

### Query params

| Param | Mặc định | Ghi chú |
|---|---|---|
| `page` | 0 | Offset pagination (khác lịch sử tin nhắn dùng cursor) |
| `size` | 20 | |

### Response 200

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "a1b2...",
        "type": "NEW_MESSAGE",
        "reference_id": "3f1c...",
        "content": "Alice Nguyen: Chao ca nha",
        "read": false,
        "created_at": "2026-09-06T10:15:30Z"
      }
    ],
    "page": 0, "size": 20, "total_elements": 1, "total_pages": 1, "has_next": false
  }
}
```

| Field | Ghi chú |
|---|---|
| `type` | `NEW_MESSAGE` \| `FRIEND_REQUEST` \| `FRIEND_ACCEPTED` \| `MISSED_CALL` \| `MENTIONED` |
| `reference_id` | ID đối tượng liên quan — **tùy `type`** mà nó là `message_id`, `friend_request_id`... |
| `content` | Tối đa 500 ký tự, đã cắt sẵn |

> ⚠️ **`reference_id` không có khóa ngoại trong DB** (nó trỏ tới nhiều bảng khác nhau tùy `type`).
> Hệ quả: đối tượng gốc bị xóa thì thông báo thành "mồ côi". Bấm vào thông báo mà không tìm thấy
> đích là trạng thái **hợp lệ** cần xử lý, không phải bug.

### Thông báo được tạo khi nào

| Điều kiện | Có tạo thông báo? |
|---|---|
| Bạn là người **gửi** tin | ❌ Không bao giờ tự nhận thông báo của mình |
| Hội thoại đang **mute** | ❌ |
| Bạn đã **rời** nhóm | ❌ |
| Còn lại | ✅ |

Thông báo được tạo **bất đồng bộ** (sau khi tin nhắn đã lưu xong) nên có độ trễ vài chục mili-giây.
Đừng gọi `GET /notifications` ngay lập tức sau khi gửi tin và mong thấy nó liền.

---

## 5.2. `GET /api/v1/notifications/unread-count`

Số thông báo chưa đọc — cho huy hiệu trên chuông.

```json
{ "success": true, "data": 7, "error": null, "timestamp": "..." }
```

---

## 5.3. `PUT /api/v1/notifications/{id}/read`

Đánh dấu một thông báo đã đọc.

```json
{ "success": true, "data": null, "error": null, "timestamp": "..." }
```

| HTTP | Mã | Khi nào |
|---|---|---|
| 404 | `NOTIFICATION_NOT_FOUND` | Không tồn tại **hoặc của người khác** |

> Trả 404 chứ không phải 403 khi đó là thông báo của người khác: người gọi không có quyền biết id đó
> **có tồn tại hay không**.

---

## 5.4. `PUT /api/v1/notifications/read-all`

Đánh dấu tất cả đã đọc. Trả về **số dòng vừa cập nhật**:

```json
{ "success": true, "data": 7, "error": null, "timestamp": "..." }
```

---

## 5.5. Nhận thông báo real-time

Client đang online nhận thông báo mới qua WebSocket tại `/user/queue/notifications`, payload chính
là `NotificationResponse` ở §5.1. Xem `13_API_REFERENCE_REALTIME.md` để biết cách kết nối.

```ts
client.subscribe('/user/queue/notifications', (f) => showToast(JSON.parse(f.body)));
```

---

## 6. WEB PUSH

Web Push cho phép hiện thông báo **ngay cả khi người dùng đã đóng tab**. Nó không đi qua WebSocket
của ta (kết nối đó đã đứt) mà qua dịch vụ đẩy của chính hãng trình duyệt — FCM với Chrome, Mozilla
autopush với Firefox.

**Server chỉ gửi Web Push cho người đang OFFLINE.** Người đang mở app vừa nhận tin qua WebSocket
rồi; bắn thêm thông báo hệ điều hành là làm phiền hai lần.

### Luồng đăng ký phía frontend

```
1. GET /notifications/push/public-key   -> nhận public_key (và enabled)
2. Đăng ký Service Worker (sw.js)
3. registration.pushManager.subscribe({ userVisibleOnly: true,
     applicationServerKey: <public_key> })
4. POST /notifications/push/subscribe   -> gửi endpoint + 2 khóa lên server
```

---

## 6.1. `GET /api/v1/notifications/push/public-key`

```json
{
  "success": true,
  "data": { "enabled": true, "public_key": "BEl62iUYgUiv..." }
}
```

| Field | Ghi chú |
|---|---|
| `enabled` | `false` khi server chưa cấu hình khóa VAPID (mặc định ở môi trường dev) |
| `public_key` | Chuỗi rỗng khi `enabled = false` |

> **Kiểm tra `enabled` trước khi hiện nút "Bật thông báo".** `false` nghĩa là tính năng chưa được
> cấu hình — cho người dùng bấm vào sẽ chỉ dẫn tới một nút không làm gì cả.
>
> Công khai khóa này là **đúng theo thiết kế** của Web Push: nó chỉ để trình duyệt xác minh chữ ký
> của server. Gửi được thông báo hay không phụ thuộc khóa **bí mật** mà server giữ.

---

## 6.2. `POST /api/v1/notifications/push/subscribe`

Đăng ký thiết bị hiện tại nhận Web Push.

### Request body

Chép từ `PushSubscription.toJSON()` của trình duyệt, **phẳng hóa một cấp**:

```json
{
  "endpoint": "https://fcm.googleapis.com/fcm/send/dQw4w9...",
  "p256dh_key": "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM=",
  "auth_key": "tBHItJI5svbpez7KI4CCXg=="
}
```

| Field | Nguồn phía trình duyệt |
|---|---|
| `endpoint` | `subscription.endpoint` |
| `p256dh_key` | `subscription.keys.p256dh` |
| `auth_key` | `subscription.keys.auth` |

> **Vì sao phải lưu cả 2 khóa, không chỉ endpoint**: nội dung thông báo được **mã hóa đầu-cuối**
> bằng chúng. Dịch vụ đẩy trung gian chỉ chuyển tiếp gói tin mà không đọc được nội dung.

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "..." }
```

**Đây là upsert theo `endpoint`, không phải insert.** Gọi lại nhiều lần trên cùng thiết bị không tạo
bản ghi trùng. Nếu đăng nhập một tài khoản **khác** trên cùng máy, thiết bị được chuyển sang tài
khoản mới — nếu không, nó sẽ tiếp tục nhận thông báo của người đã đăng xuất.

---

## 6.3. `DELETE /api/v1/notifications/push/subscribe`

### Query params

| Param | Bắt buộc |
|---|---|
| `endpoint` | ✅ |

```bash
curl -X DELETE "http://localhost:8080/api/v1/notifications/push/subscribe?endpoint=$ENDPOINT" \
  -H "Authorization: Bearer $TOKEN"
```

Idempotent. Cố ý **không** kiểm tra endpoint có thuộc về người gọi hay không: biết được endpoint
nghĩa là đang ngồi trên chính thiết bị đó, và bắt buộc đúng chủ sẽ làm hỏng ca dùng thật — đăng xuất
tài khoản cũ rồi mới gỡ đăng ký.

> **Server tự dọn subscription chết**: khi dịch vụ đẩy trả `404`/`410` (người dùng gỡ app, xóa dữ
> liệu site), bản ghi bị xóa tự động. Frontend không cần làm gì.

---

## 7. BẢNG TỔNG HỢP MÃ LỖI

| Mã | HTTP | Endpoint | Nguyên nhân |
|---|---|---|---|
| `FILE_EMPTY` | 400 | upload | Không chọn file / file 0 byte |
| `FILE_TOO_LARGE` | 413 | upload | Vượt hạn mức của `category` |
| `FILE_TYPE_NOT_ALLOWED` | 415 | upload, gửi tin | Kiểu thật ngoài allowlist; **hoặc** `file_url` trỏ ra ngoài bucket |
| `FILE_UPLOAD_FAILED` | 500 | upload | MinIO không phản hồi |
| `MESSAGE_CONTENT_REQUIRED` | 400 | gửi tin | Không có chữ **và** không có tệp |
| `ATTACHMENT_REQUIRED` | 400 | gửi tin | `type=IMAGE/FILE/VOICE` nhưng thiếu tệp |
| `MESSAGE_NOT_FOUND` | 404 | reaction, forward, for-me | Tin không tồn tại/đã xóa |
| `MESSAGE_ALREADY_RECALLED` | 409 | reaction, forward | Tin đã thu hồi |
| `NOT_CONVERSATION_MEMBER` | 403 | reaction, forward, for-me, mute | Không phải thành viên |
| `USER_BLOCKED` | 403 | forward | Hội thoại đích DIRECT, hai bên đã chặn nhau |
| `NOTIFICATION_NOT_FOUND` | 404 | mark read | Không tồn tại hoặc của người khác |
| `VALIDATION_ERROR` | 400 | mute, gửi tin | `muted_until` ở quá khứ; quá 10 tệp |

---

## 8. KỊCH BẢN TEST END-TO-END

```bash
# 0. Hạ tầng + app
docker compose -f infra/docker-compose.yml up -d
./mvnw spring-boot:run

# 1. Đăng nhập 2 user -> $ALICE, $BOB (xem 07_API_REFERENCE_AUTH.md)
#    Tạo hội thoại 1-1 -> $CONV (xem 11_API_REFERENCE_CHAT.md §3.2)

# 2. THỬ CHỐT MAGIC BYTE: file .exe đổi đuôi thành .jpg
printf 'MZ\x90\x00\x03\x00\x00\x00' > /tmp/fake.jpg
curl -X POST http://localhost:8080/api/v1/media/upload \
  -H "Authorization: Bearer $ALICE" -F "file=@/tmp/fake.jpg" -F "category=IMAGE"
# -> 415 FILE_TYPE_NOT_ALLOWED   ✅ đuôi .jpg không cứu được nó

# 3. Upload ảnh thật -> chép file_url
curl -X POST http://localhost:8080/api/v1/media/upload \
  -H "Authorization: Bearer $ALICE" -F "file=@anh-that.png" -F "category=IMAGE"

# 4. Gửi tin nhắn kèm ảnh
curl -X POST http://localhost:8080/api/v1/conversations/$CONV/messages \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d '{"type":"IMAGE","attachments":[{
        "file_url":"<file_url bước 3>","file_name":"anh-that.png",
        "file_type":"image/png","file_size":20480}]}'

# 5. Bob thả tim, rồi thả lại đúng tim đó để gỡ (toggle)
curl -X PUT http://localhost:8080/api/v1/messages/$MSG/reactions \
  -H "Authorization: Bearer $BOB" -H "Content-Type: application/json" -d '{"emoji":"❤️"}'
curl -X PUT http://localhost:8080/api/v1/messages/$MSG/reactions \
  -H "Authorization: Bearer $BOB" -H "Content-Type: application/json" -d '{"emoji":"❤️"}'
# -> lần 2 trả về reactions: []

# 6. Bob xem thông báo (chờ ~100ms vì tạo bất đồng bộ)
curl -H "Authorization: Bearer $BOB" http://localhost:8080/api/v1/notifications/unread-count

# 7. Bob tắt thông báo 8 tiếng -> Alice gửi tiếp -> unread-count KHÔNG tăng
curl -X PUT http://localhost:8080/api/v1/conversations/$CONV/mute \
  -H "Authorization: Bearer $BOB" -H "Content-Type: application/json" \
  -d '{"muted_until":"2026-09-07T00:00:00Z"}'

# 8. Bob ẩn 1 tin phía mình -> Bob không thấy nữa, Alice vẫn thấy đủ
curl -X DELETE http://localhost:8080/api/v1/messages/$MSG/for-me \
  -H "Authorization: Bearer $BOB"
curl -H "Authorization: Bearer $BOB"  "http://localhost:8080/api/v1/conversations/$CONV/messages"
curl -H "Authorization: Bearer $ALICE" "http://localhost:8080/api/v1/conversations/$CONV/messages"
```

**Kiểm tra file đã lên thật**: MinIO Console `http://localhost:9001`
(`chatsphere_admin` / `minio_dev_password`) → bucket `chatsphere-media` → thư mục theo ngày.

---

*Hết tài liệu 15_API_REFERENCE_MEDIA_NOTIFICATION.md — xem
`14_PHASE5_MEDIA_NOTIFICATION_REPORT.md` để hiểu vì sao từng quyết định thiết kế ở trên được chọn.*
