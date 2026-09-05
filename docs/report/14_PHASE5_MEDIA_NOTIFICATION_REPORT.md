# BÁO CÁO PHASE 5 — MEDIA & NOTIFICATION MODULE

**Dự án**: ChatSphere backend · **Stack**: Spring Boot 4.1.1, Java 21, PostgreSQL 16, Redis 7, MinIO
**Trạng thái**: hoàn thành phần backend, 105/105 test xanh (27 test mới của Phase 5, cộng 78 test Phase 1-4 không bị vỡ)
**Tài liệu liên quan**: `01_SYSTEM_DESIGN.md` (§7.3.9-7.3.15 data model), `03_CODE_ROADMAP.md` (§Phase 5),
`12_PHASE4_REALTIME_REPORT.md` (`MessageSentEvent` mà Phase này cắm vào)

---

## MỤC LỤC

1. [Phạm vi đã làm](#1-phạm-vi-đã-làm)
2. [Bản đồ file](#2-bản-đồ-file)
3. [Mô hình dữ liệu](#3-mô-hình-dữ-liệu)
4. [Bảng endpoint](#4-bảng-endpoint)
5. [Luồng nghiệp vụ chi tiết](#5-luồng-nghiệp-vụ-chi-tiết)
6. [Lý thuyết nền](#6-lý-thuyết-nền)
7. [Bản đồ mã lỗi](#7-bản-đồ-mã-lỗi)
8. [Bảo mật & riêng tư](#8-bảo-mật--riêng-tư)
9. [Test](#9-test)
10. [Vướng mắc kỹ thuật đã gặp](#10-vướng-mắc-kỹ-thuật-đã-gặp)
11. [Nợ kỹ thuật](#11-nợ-kỹ-thuật)
12. [Chạy thử bằng tay](#12-chạy-thử-bằng-tay)

---

## 1. PHẠM VI ĐÃ LÀM

| UC | Tên | Endpoint / cơ chế chính | Trạng thái |
|---|---|---|---|
| UC-19 | Gửi ảnh/file/voice | `POST /media/upload` + `attachments` trong `sendMessage` | ✅ |
| UC-21 | Chuyển tiếp tin nhắn | `POST /messages/{id}/forward` | ✅ |
| UC-22 | Thả cảm xúc | `PUT /messages/{id}/reactions` | ✅ |
| UC-23 | Web Push khi đóng tab | `PushNotificationService` + `/notifications/push/*` | ✅ (backend) |
| UC-26 | Danh sách thông báo | `GET /notifications` | ✅ |
| UC-27 | Tắt thông báo hội thoại | `PUT /conversations/{id}/mute` | ✅ |
| UC-28 | Xóa tin nhắn phía mình | `DELETE /messages/{id}/for-me` | ✅ |
| 5.3 (frontend) | Service Worker `sw.js`, xin quyền, subscribe | — | ⏸ **Không thuộc repo này** |

**Quy mô**: 34 file Java mới (main) — 7 `media`, 14 `notification`, 13 bổ sung cho `chat` — cộng 3 file
test, **5 migration** (V11→V15), 3 dependency mới trong `pom.xml`, 3 file yaml được bổ sung cấu hình.

**Điểm nối với Phase 4**: toàn bộ module Notification cắm vào `MessageSentEvent` — sự kiện đã được
dựng ở Phase 4 — nên **không sửa một dòng nào** của `MessageService.sendMessage()`. Đây đúng là lợi
ích đã dự đoán khi chọn kiến trúc event ở Phase trước.

---

## 2. BẢN ĐỒ FILE

```
com.chatsphere
│
├── media/                                (7 file — module mới)
│   ├── MinioProperties.java              app.minio.* ; publicUrl mặc định = endpoint
│   ├── MinioConfig.java                  bean MinioClient
│   ├── MinioBucketInitializer.java       @PostConstruct: tạo bucket + đặt policy public-read
│   ├── MediaCategory.java                IMAGE/VOICE/FILE — allowlist MIME + hạn mức riêng
│   ├── MediaService.java                 ★ kiểm tra magic byte, đặt tên object, upload
│   ├── UploadedFile.java                 kết quả upload, cũng là thứ client gửi lại khi đính kèm
│   └── MediaController.java              POST /api/v1/media/upload
│
├── notification/                         (14 file — module mới)
│   ├── NotificationConfig.java           bật binding app.push.*
│   ├── domain/       Notification, NotificationType, PushSubscription
│   ├── repository/   NotificationRepository (bulk markAllAsRead), PushSubscriptionRepository
│   ├── dto/          NotificationResponse, PushSubscriptionRequest, VapidPublicKeyResponse
│   ├── service/
│   │   ├── NotificationService.java          lưu DB trước, đẩy WS sau
│   │   ├── NotificationEventListener.java    ★ @Async + AFTER_COMMIT trên MessageSentEvent
│   │   ├── PushNotificationService.java      web-push + VAPID, tự dọn subscription chết
│   │   ├── PushSubscriptionService.java      upsert theo endpoint
│   │   └── VapidProperties.java              mặc định TẮT ở dev
│   └── controller/   NotificationController  7 endpoint
│
└── chat/                                 (13 file mới, 7 file sửa)
    ├── domain/    MessageAttachment, MessageReaction, MessageDeletion, MessageDeletionId
    ├── repository/MessageAttachmentRepository, MessageReactionRepository, MessageDeletionRepository
    └── dto/       AttachmentRequest, AttachmentResponse, ReactionRequest, ReactionResponse,
                   ForwardMessageRequest, MuteConversationRequest
```

**File Phase trước bị sửa:**

| File | Thay đổi |
|---|---|
| `MessageService` | attachment khi gửi, `reactToMessage()`, `forwardMessage()`, `deleteForMe()`, nạp theo lô ở `getMessages()` |
| `ConversationService` | `muteConversation()` |
| `MessageResponse` | thêm `attachments`, `reactions` (luôn là mảng, không null) |
| `SendMessageRequest` | `content` bỏ `@NotBlank`, thêm `attachments` |
| `WsSendMessageRequest` | thêm `attachments` — luồng WebSocket dùng chung quy tắc |
| `ConversationMapper` | bản `toMessageResponse` 3 tham số; giữ overload 1 tham số cho tin chữ thuần |
| `ConversationParticipantRepository` | `findNotifiableUserIds()` — lọc mute ngay trong SQL |
| `AsyncConfig` | thêm `notificationExecutor` |
| `ErrorCode` | thêm 9 mã |

---

## 3. MÔ HÌNH DỮ LIỆU

### 3.1. ERD phần Phase 5

```
        users ──────┐                      messages ────┐
          │         │                          │        │
          │         └──< push_subscriptions    │        ├──< message_attachments
          │         └──< notifications         │        ├──< message_reactions
          │                                    │        └──< message_deletions >── users
          └────────────────────────────────────┘
```

| Bảng | Migration | Khóa/index đáng chú ý |
|---|---|---|
| `message_attachments` | V11 | `idx_..._message_id` — luôn đọc theo tin nhắn |
| `message_reactions` | V12 | **UNIQUE (message_id, user_id)** — 1 người 1 reaction |
| `message_deletions` | V13 | **PK tổ hợp (message_id, user_id)** |
| `notifications` | V14 | `(user_id, created_at DESC)` + **partial index** `WHERE is_read = FALSE` |
| `push_subscriptions` | V15 | **UNIQUE (endpoint)** |

### 3.2. Vì sao `message_deletions` dùng khóa chính tổ hợp, lệch thiết kế gốc

`01_SYSTEM_DESIGN.md` §7.3.9 đặt một cột `id UUID PK` riêng. Bản cài đặt bỏ nó và dùng
`PRIMARY KEY (message_id, user_id)`.

Bảng này thuần túy là quan hệ nhiều-nhiều "ai đã ẩn tin nào", **không bao giờ được tham chiếu từ nơi
khác** — không có bảng nào cần khóa ngoại trỏ tới một dòng cụ thể của nó. Một cột `id` nữa chỉ tốn
chỗ và thêm một index phải bảo trì, trong khi ràng buộc thật sự cần (mỗi người ẩn mỗi tin đúng một
lần) lại chính là cặp khóa đó.

Cái giá: cần `@EmbeddedId` + `MessageDeletionId` với `@EqualsAndHashCode` — bắt buộc chứ không phải
tùy chọn phong cách, vì Hibernate dùng equals/hashCode của khóa để nhận biết hai tham chiếu có cùng
trỏ về một dòng hay không.

### 3.3. Partial index cho huy hiệu "chưa đọc"

```sql
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE is_read = FALSE;
```

Huy hiệu số thông báo chưa đọc hiện trên **mọi màn hình**, nên `countByUserIdAndReadIsFalse` là truy
vấn chạy nhiều nhất của module này. Partial index chỉ chứa dòng chưa đọc — nhỏ hơn nhiều lần index
đầy đủ vì đại đa số thông báo cũ đã đọc, và nó co lại theo thời gian thay vì phình ra.

### 3.4. `notifications.reference_id` cố ý KHÔNG có khóa ngoại

Nó trỏ tới bảng nào là tùy `type`: `message_id` với `NEW_MESSAGE`, `friend_request_id` với
`FRIEND_REQUEST`, `call_session_id` với `MISSED_CALL`. Không kiểu khóa ngoại nào diễn đạt được điều đó.

Đánh đổi: mất kiểm tra toàn vẹn ở tầng DB, đổi lấy **một bảng thông báo dùng chung cho mọi loại sự
kiện** thay vì mỗi loại một bảng. Hệ quả client phải biết: đối tượng gốc bị xóa thì thông báo thành
"mồ côi", nên bấm vào thông báo mà không tìm thấy đích là trạng thái **hợp lệ** cần xử lý, không
phải bug.

---

## 4. BẢNG ENDPOINT

| Method | Endpoint | Mô tả | UC |
|---|---|---|---|
| POST | `/api/v1/media/upload` | Tải file, kiểm tra magic byte | UC-19 |
| PUT | `/api/v1/messages/{id}/reactions` | Thả/đổi/gỡ cảm xúc (toggle) | UC-22 |
| POST | `/api/v1/messages/{id}/forward` | Chuyển tiếp sang hội thoại khác | UC-21 |
| DELETE | `/api/v1/messages/{id}/for-me` | Ẩn tin phía mình | UC-28 |
| PUT | `/api/v1/conversations/{id}/mute` | Tắt thông báo tới thời điểm (null = bật lại) | UC-27 |
| GET | `/api/v1/notifications` | Danh sách thông báo, mới nhất trước | UC-26 |
| GET | `/api/v1/notifications/unread-count` | Số chưa đọc cho huy hiệu | UC-26 |
| PUT | `/api/v1/notifications/{id}/read` | Đánh dấu 1 cái đã đọc | UC-26 |
| PUT | `/api/v1/notifications/read-all` | Đánh dấu tất cả đã đọc | UC-26 |
| GET | `/api/v1/notifications/push/public-key` | Khóa công khai VAPID | UC-23 |
| POST | `/api/v1/notifications/push/subscribe` | Đăng ký thiết bị nhận push | UC-23 |
| DELETE | `/api/v1/notifications/push/subscribe` | Hủy đăng ký | UC-23 |

Ngoài ra `POST /conversations/{id}/messages` và `/app/chat.sendMessage` nhận thêm field `attachments`.
Kênh WebSocket bổ sung `/user/queue/notifications` (server → client).

> Tài liệu API chi tiết cho nhóm endpoint này (`15_API_REFERENCE_MEDIA_NOTIFICATION.md`) **chưa
> viết** — hiện dùng Swagger UI tại `/swagger-ui.html`.

---

## 5. LUỒNG NGHIỆP VỤ CHI TIẾT

### 5.1. Upload — không bao giờ tin phần mở rộng tên file

Đây là điểm học thuật quan trọng nhất của Phase 5.

`Content-Type` trong request và đuôi `.jpg` đều do **client tự khai**, sửa lại dễ như đổi tên file.
Cách duy nhất đáng tin là đọc vài byte đầu của nội dung thật — "magic byte" / chữ ký định dạng:

| Định dạng | Chữ ký |
|---|---|
| PNG | `89 50 4E 47 0D 0A 1A 0A` |
| JPEG | `FF D8 FF` |
| PDF | `%PDF` |
| Windows EXE | `MZ` |

Apache Tika giữ sẵn bảng tra này. Điểm tinh tế trong cài đặt:

```java
byte[] head = in.readNBytes(64);
return tika.detect(head);          // CHỈ truyền nội dung
```

Cố ý **không** dùng `tika.detect(bytes, fileName)` — bản đó sẽ ưu tiên đuôi file khi nội dung mơ hồ,
đúng thứ ta đang muốn loại bỏ.

**Vì sao nghiêm trọng đến vậy**: một file `.exe` đổi tên thành `.jpg` mà lọt lên storage rồi phát tán
qua link chat chính là kênh phát tán mã độc — và server đã ký tên bảo chứng cho nó bằng tên miền của
mình.

Thứ tự kiểm tra cũng có chủ ý — **rẻ trước, đắt sau**: rỗng → kích thước → kiểu thật → mới upload.
Đọc kiểu file của một file 25MB rồi mới phát hiện nó vượt hạn mức là lãng phí; và không bao giờ được
đẩy byte nào lên storage trước khi mọi kiểm tra đã qua (test `chan_file_exe_doi_duoi_thanh_jpg` kiểm
tra đúng điều này bằng `verify(minioClient, never()).putObject(any())`).

### 5.2. Tên object — UUID chứ không phải tên gốc

```
yyyy/MM/dd/<uuid>.<ext>
```

Một lựa chọn giải quyết **ba** vấn đề cùng lúc:

1. Hai người cùng gửi `anh.jpg` sẽ ghi đè lên nhau.
2. Tên file chứa `../` leo ra ngoài thư mục (path traversal) — test
   `ten_object_khong_bao_gio_mang_theo_ky_tu_duong_dan` phủ ca này.
3. Tên file tiết lộ thông tin riêng tư (`hop-dong-luong-2026.pdf`).

Đuôi file được lọc qua `[a-z0-9]{1,10}` nên nó không bao giờ tự mang theo ký tự đường dẫn. Tên gốc
vẫn được giữ ở cột `file_name` để hiển thị cho người nhận.

### 5.3. Hai bước upload rồi mới gửi tin — và chốt `assertManagedUrl`

Luồng gồm 2 request tách rời: `POST /media/upload` trả metadata, rồi client gọi gửi tin nhắn kèm
`file_url`. Không gộp thành một multipart duy nhất vì: người dùng chọn ảnh xong là upload chạy nền
ngay trong lúc họ còn gõ chú thích, nên lúc bấm Gửi thì tin bay đi tức thì; gửi lại tin thất bại
cũng không phải tải lên lần nữa.

Nhược điểm lộ ra ngay: client có thể gửi `file_url` **bất kỳ**. Chốt chặn:

```java
public void assertManagedUrl(String fileUrl) {
    String expectedPrefix = "%s/%s/".formatted(props.publicUrl(), props.bucket());
    if (fileUrl == null || !fileUrl.startsWith(expectedPrefix)) throw ...;
}
```

Không có nó, giao diện sẽ hiển thị một URL ngoài Internet y như tệp nội bộ đã được kiểm duyệt, trong
khi nội dung nằm ngoài tầm kiểm soát và có thể đổi bất cứ lúc nào **sau khi** gửi.

Còn `file_size`/`file_type` do client khai thì **chấp nhận** — chúng chỉ để hiển thị, còn bản thân
FILE đã được kiểm tra kỹ ở bước upload. Đó mới là chỗ có rủi ro bảo mật.

### 5.4. Ràng buộc liên-field khi gửi tin nhắn

Phase 5 nới `content` khỏi `@NotBlank` (tin chỉ có ảnh là hợp lệ), thay bằng quy tắc kiểm tra trong
service:

```java
if (!hasContent && !hasAttachment)                          -> MESSAGE_CONTENT_REQUIRED
if (type ∉ {TEXT, SYSTEM} && !hasAttachment)                -> ATTACHMENT_REQUIRED
```

Không diễn đạt được bằng annotation trên từng field vì nó là ràng buộc **giữa** các field. Vế thứ
hai chặn dữ liệu tự mâu thuẫn: `type=IMAGE` mà không có tệp thì client sẽ dựng một khung ảnh rỗng.

### 5.5. Reaction — toggle, và unique index là chốt thật sự

Gửi lại đúng emoji đang có nghĩa là **gỡ**; gửi emoji khác nghĩa là **đổi**. Đúng thói quen người
dùng, và cũng là cách duy nhất để bỏ reaction mà không cần thêm một endpoint DELETE riêng.

Kiểm tra "đã có chưa?" ở tầng Java **không đủ**: hai request thả reaction gần như đồng thời
(double-click, mạng gửi lặp) sẽ cùng vượt qua bước kiểm tra rồi cùng INSERT. Chỉ
`UNIQUE (message_id, user_id)` mới thật sự chặn được — và service nuốt
`DataIntegrityViolationException` vì trạng thái cuối cùng vẫn **đúng** (đã có reaction), giống hệt
cách `BlockService` xử lý ở Phase 2.

Reaction được **gom theo emoji** trước khi trả về:

```json
{ "emoji": "❤️", "count": 2, "user_ids": ["...", "..."] }
```

Giao diện cần đúng thứ này ("❤️ 3" kèm danh sách khi rê chuột). Trả 3 dòng rời rồi bắt mỗi client tự
gom là chép cùng một đoạn logic sang mọi nền tảng (web, iOS, Android) và chắc chắn sẽ có chỗ gom sai.
Sắp xếp nhiều-emoji-nhất trước, rồi tới thứ tự chữ cái, để kết quả **ổn định** giữa các lần gọi —
tránh giao diện nhảy lung tung khi hai emoji cùng số lượng.

### 5.6. Chuyển tiếp — sao chép nội dung, không trỏ tới tin gốc

Nếu chỉ lưu con trỏ tới tin gốc, người gửi gốc **thu hồi** tin của họ là bản chuyển tiếp ở hội thoại
khác cũng trống theo. Người nhận bản chuyển tiếp chưa từng đồng ý điều đó, và họ cũng không nhìn thấy
hội thoại gốc để hiểu chuyện gì vừa xảy ra.

Vì vậy nội dung được **sao chép**; `forwarded_from_message_id` vẫn lưu nhưng chỉ để hiển thị nhãn
"Đã chuyển tiếp".

Đính kèm cũng được nhân bản ở tầng metadata nhưng **cùng trỏ tới một object trên storage** — không
tải lại file. Đây chính là lý do việc xóa file vật lý phải do job dọn dẹp đếm tham chiếu đảm nhiệm,
không thể xóa ngay khi một tin nhắn biến mất (xem §11).

Quyền: người chuyển tiếp phải là thành viên của **cả hai** hội thoại — nguồn (để được đọc tin) và
đích (để được gửi). Thiếu vế đầu thì chuyển tiếp trở thành cách đọc trộm hội thoại người khác; test
`khong_the_chuyen_tiep_tin_cua_hoi_thoai_minh_khong_tham_gia` phủ ca này.

### 5.7. Ba khái niệm "xóa" dễ nhầm

| Khái niệm | Cơ chế | Ai thấy thay đổi | Ai làm được | Giới hạn |
|---|---|---|---|---|
| **Thu hồi** (UC-20) | `messages.status = RECALLED`, xóa `content` thật | Mọi người | Chỉ người gửi | Trong 5 phút |
| **Xóa phía tôi** (UC-28) | Dòng trong `message_deletions` | Chỉ chính mình | Ai cũng được | Không |
| **Soft delete** | `messages.deleted_at` | Mọi người | (nội bộ) | — |

Vì mỗi người có tầm nhìn riêng nên "xóa phía tôi" **không thể** biểu diễn bằng một cột cờ trên
`messages`. Và nó cố ý **không** phát sự kiện real-time: đây là thay đổi riêng tư của một người,
phát cho cả hội thoại sẽ tiết lộ chính xác điều họ vừa muốn giấu.

Lọc diễn ra **sau** khi phân trang, không phải bằng `NOT EXISTS` trong query lấy tin:

```java
UUID nextCursor = hasNext ? page.getLast().getId() : null;   // TÍNH TRƯỚC khi lọc
return new CursorPageResponse<>(toResponses(page, currentUserId), nextCursor, hasNext);
```

Thêm subquery vào truy vấn nóng nhất hệ thống sẽ làm chậm **mọi** lần mở hội thoại, trong khi "xóa
phía tôi" là thao tác hiếm. Đánh đổi: trang trả về có thể ít hơn `limit` vài tin — chấp nhận được với
cursor pagination. Lưu ý cursor phải tính **trước** khi lọc, nếu không trang sau sẽ bỏ sót tin.

### 5.8. Thông báo — tách khỏi luồng gửi tin, hai tầng chờ

```java
@Async("notificationExecutor")
@TransactionalEventListener(phase = AFTER_COMMIT)
public void onMessageSent(MessageSentEvent event) { ... }
```

**Vì sao tách?** Gửi tin nhắn là thao tác nóng nhất và người dùng cảm nhận trực tiếp độ trễ. Một nhóm
50 người cần 50 lần INSERT thông báo cộng các lần gọi Web Push ra mạng ngoài — nhét vào luồng gửi tin
thì thời gian phản hồi phụ thuộc vào kích thước nhóm và vào một dịch vụ bên thứ ba.

**Thứ tự hai annotation quan trọng**: chờ commit trước (để không bao giờ tạo thông báo cho một tin
nhắn rốt cuộc bị rollback), rồi mới nhảy sang luồng nền (để không giữ chân luồng vừa gửi tin). Ngược
lại sẽ có lúc luồng nền đọc DB mà chưa thấy tin nhắn.

Trong vòng lặp, mỗi người nhận được bọc `try/catch` riêng: một người lỗi (tài khoản vừa bị xóa,
endpoint push hỏng) **không** được làm hỏng thông báo của những người còn lại trong nhóm.

### 5.9. Mute — lưu mốc hết hạn, không phải cờ boolean

```sql
AND (p.mutedUntil IS NULL OR p.mutedUntil <= :now)
```

Người dùng chọn "tắt 8 tiếng" thì hệ thống phải **tự bật lại**. Với cờ boolean ta sẽ cần thêm một job
quét định kỳ; với mốc thời gian thì chỉ cần một phép so sánh ngay trong câu chọn người nhận — không
job, không trạng thái phải đồng bộ. Test `mute_het_han_thi_tu_dong_nhan_thong_bao_tro_lai` xác nhận
điều đó bằng cách truyền `now` ở tương lai.

Lọc mute nằm **trong SQL** chứ không ở tầng Java, nên hội thoại đang mute không tốn một vòng lặp nào.
Truy vấn cũng trả thẳng `UUID` thay vì entity — listener chạy trên luồng nền, ngoài transaction, nên
chạm vào quan hệ LAZY ở đó sẽ ném `LazyInitializationException`.

### 5.10. Web Push — chỉ gửi cho người OFFLINE

```java
notificationService.create(recipientId, NEW_MESSAGE, message.id(), content);
if (!presenceService.isOnline(recipientId)) {
    pushNotificationService.sendToUser(recipientId, "Tin nhắn mới", content);
}
```

Người đang mở app vừa nhận tin qua WebSocket rồi; bắn thêm thông báo hệ điều hành là làm phiền hai
lần. Đây chính là chỗ module Presence của Phase 4 được dùng lại.

Web Push khác thông báo trong app ở điểm căn bản: nó **không** đi qua kết nối WebSocket của ta (kết
nối đó đã đứt khi tab đóng) mà qua dịch vụ đẩy của chính hãng trình duyệt — FCM với Chrome, Mozilla
autopush với Firefox. Server gửi một gói **đã mã hóa** tới endpoint đó; dịch vụ trung gian chuyển
tiếp mà không đọc được nội dung, Service Worker phía máy người dùng mới giải mã và hiển thị. Đó là
lý do bảng `push_subscriptions` phải lưu cả `p256dh_key` và `auth_key`, không chỉ endpoint.

**Tự dọn subscription chết**: HTTP 404/410 từ dịch vụ đẩy là tín hiệu **duy nhất** cho biết trình
duyệt đã hủy đăng ký (gỡ app, xóa dữ liệu site). Giữ lại thì mỗi thông báo về sau đều tốn một lượt
gọi mạng chắc chắn thất bại.

---

## 6. LÝ THUYẾT NỀN

### 6.1. Allowlist, không phải blocklist

`MediaCategory` liệt kê MIME **được phép**. Blocklist luôn thua: mỗi định dạng nguy hiểm mới xuất
hiện là một lỗ hổng, còn allowlist thì cái gì chưa biết mặc định bị chặn.

Hạn mức chia theo nhóm (IMAGE 10MB / VOICE 10MB / FILE 25MB) chứ không dùng một con số chung: một
tấm ảnh 25MB gần như luôn là ảnh chưa nén gửi nhầm, còn một tài liệu 25MB thì bình thường. Hạn mức
chung buộc phải chọn — đủ rộng cho tài liệu thì mở toang cho ảnh, đủ chặt cho ảnh thì chặn oan tài liệu.

Test `chan_file_dung_dinh_dang_nhung_sai_nhom` xác nhận allowlist tính theo **từng nhóm**: PNG hợp lệ
nhưng gửi vào nhóm VOICE vẫn bị chặn.

### 6.2. Nạp theo lô — 3 query cố định cho cả trang

`getMessages()` cần attachment + reaction + danh sách tin đã ẩn cho mỗi tin nhắn. Để mapper tự đi lấy
từng thứ cho từng tin thì một trang 30 tin sẽ bắn **90 query**. Thay vào đó nạp theo lô:

```java
List<UUID> messageIds = messages.stream().map(Message::getId).toList();
attachmentRepository.findByMessageIds(messageIds)   // 1
reactionRepository.findByMessageIds(messageIds)     // 2
deletionRepository.findDeletedMessageIds(...)       // 3
```

Chi phí không phụ thuộc số tin nhắn. Đây đúng là kỹ thuật Phase 3 đã dùng cho participant và unread
count — được nhắc lại vì nó là cái bẫy dễ tái phạm nhất mỗi khi thêm một quan hệ mới vào DTO.

### 6.3. Mapper vẫn stateless — vì sao không thêm `@OneToMany` vào `Message`

Cách nhanh nhất để có `attachments` trong `MessageResponse` là thêm `@OneToMany` vào entity `Message`
rồi để MapStruct tự map. Không làm vậy, vì:

1. Mapper sẽ **tự ý bắn query** — chính là N+1 ở §6.2, chỉ là ẩn kỹ hơn.
2. `ChatRealtimeBroadcaster` chạy **sau khi transaction đã commit**; chạm vào collection LAZY ở đó sẽ
   ném `LazyInitializationException`.

Nên giữ nguyên nguyên tắc đã đặt từ Phase 2-3: **Service tính trước rồi truyền vào mapper**. Cụ thể
là bản `toMessageResponse(message, attachments, reactions)` 3 tham số, kèm overload 1 tham số trả
danh sách rỗng cho tin chữ thuần và cho `lastMessage` ở danh sách hội thoại.

### 6.4. Bulk UPDATE cho "đánh dấu tất cả đã đọc"

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
```

Người dùng có thể có hàng nghìn thông báo chưa đọc; nạp hết vào persistence context chỉ để lật một cờ
boolean là lãng phí bộ nhớ và sinh ra hàng nghìn câu UPDATE riêng lẻ.

`clearAutomatically = true` là bắt buộc: bulk update đi **thẳng xuống DB**, không qua persistence
context — không xóa cache thì entity đang giữ trong bộ nhớ vẫn mang giá trị cũ và có thể ghi đè ngược
lại kết quả vừa cập nhật.

### 6.5. Bucket public-read — đánh đổi có ý thức

Policy hiện tại cho phép **ai có URL cũng tải được**, kể cả người ngoài hội thoại. Chấp nhận được ở
phạm vi học tập vì:

- Tên object là UUID ngẫu nhiên 128 bit — không đoán được.
- Policy chỉ cho `GetObject`, **không** cho `ListBucket` — không liệt kê được.
- Đổi lại thẻ `<img src>` hoạt động thẳng, không cần proxy hay ký URL.

Hệ thống thật chứa dữ liệu nhạy cảm phải dùng bucket private + presigned URL có hạn. Đã ghi rõ trong
Javadoc của `MinioBucketInitializer.publicReadPolicy()` và ở §11.

### 6.6. MinIO chết không được làm sập ứng dụng chat

`MinioBucketInitializer` bắt mọi exception và **chỉ log**. Nếu ném tiếp, MinIO chưa kịp khởi động sẽ
làm cả backend không lên được — trong khi chat chữ, đăng nhập, kết bạn hoàn toàn không phụ thuộc
object storage. Hỏng thì chỉ upload hỏng, và `MediaService` báo `FILE_UPLOAD_FAILED` cho đúng thao
tác đó.

Cùng tinh thần: `app.push.enabled=false` (mặc định ở dev) làm Web Push im lặng bỏ qua thay vì làm cả
luồng thông báo đổ lỗi. `VapidProperties` còn tự hạ `enabled` về `false` khi bật mà thiếu khóa — sai
cấu hình không được phép làm sập ứng dụng, nhưng cũng không được im lặng giả vờ đang hoạt động (nên
có dòng log ở `PushNotificationService.init()`).

---

## 7. BẢN ĐỒ MÃ LỖI

| Mã | HTTP | Ném ở đâu |
|---|---|---|
| `FILE_EMPTY` | 400 | Upload không chọn file hoặc file 0 byte |
| `FILE_TOO_LARGE` | 413 | Vượt hạn mức của `MediaCategory` |
| `FILE_TYPE_NOT_ALLOWED` | 415 | Magic byte không nằm trong allowlist của nhóm; **hoặc** `file_url` đính kèm trỏ ra ngoài bucket |
| `FILE_UPLOAD_FAILED` | 500 | MinIO không phản hồi / lỗi đọc stream |
| `MESSAGE_CONTENT_REQUIRED` | 400 | Không có chữ **và** không có tệp |
| `ATTACHMENT_REQUIRED` | 400 | `type=IMAGE/FILE/VOICE` nhưng thiếu tệp |
| `NOTIFICATION_NOT_FOUND` | 404 | Đánh dấu đã đọc một thông báo không tồn tại **hoặc của người khác** |
| `INVALID_EMOJI` | 400 | (dự phòng, hiện validate bằng `@Size` ở DTO) |
| `PUSH_SUBSCRIPTION_INVALID` | 400 | (dự phòng) |
| `MESSAGE_ALREADY_RECALLED` | 409 | React hoặc forward một tin đã thu hồi |
| `NOT_CONVERSATION_MEMBER` | 403 | (Phase 3, dùng lại) react/forward/deleteForMe ngoài hội thoại của mình |
| `VALIDATION_ERROR` | 400 | `mutedUntil` nằm ở quá khứ |

---

## 8. BẢO MẬT & RIÊNG TƯ

| Nguy cơ | Biện pháp | Ở đâu |
|---|---|---|
| **Phát tán mã độc qua file đổi đuôi** | Nhận diện MIME bằng magic byte, allowlist theo nhóm | `MediaService.detectRealMimeType` |
| Ghi đè file của người khác | Tên object là UUID ngẫu nhiên | `MediaService.buildObjectName` |
| Path traversal qua tên file | Đuôi lọc `[a-z0-9]{1,10}`; tên hiển thị cắt mọi thành phần đường dẫn | `extensionOf`, `safeFileName` |
| Đính kèm URL ngoài giả dạng tệp nội bộ | `assertManagedUrl` bắt buộc tiền tố bucket | `MediaService`, gọi từ `saveAttachments` |
| Cạn RAM khi nhiều người upload file lớn | Stream thẳng lên MinIO theo từng phần (`stream(in, -1, 10MB)`) | `MediaService.upload` |
| Đọc trộm tin qua chuyển tiếp | Bắt buộc là thành viên của **cả** hội thoại nguồn và đích | `MessageService.forwardMessage` |
| React vào tin của hội thoại lạ | `getActiveParticipantOrThrow` trước mọi thao tác | `MessageService.reactToMessage` |
| Đánh dấu đã đọc thông báo của người khác | Lọc theo `user.id` rồi trả `NOTIFICATION_NOT_FOUND` (**không** 403) | `NotificationService.markAsRead` |
| Thiết bị cũ nhận thông báo của tài khoản đã đăng xuất | `subscribe()` là upsert theo `endpoint`, chuyển chủ sở hữu sang tài khoản mới | `PushSubscriptionService.subscribe` |
| JSON payload push vỡ vì nội dung tin nhắn | Tự escape `"`, `\`, xuống dòng, ký tự điều khiển | `PushNotificationService.jsonString` |
| Nội dung tin nhắn lộ ra dịch vụ đẩy trung gian | Web Push mã hóa đầu-cuối bằng `p256dh`/`auth` — FCM chỉ chuyển tiếp | thư viện `web-push` |

> Ghi chú về `NOTIFICATION_NOT_FOUND` thay vì 403: người gọi không có quyền biết id đó **có tồn tại
> hay không** — cùng nguyên tắc đã áp dụng cho `USER_BLOCKED` ở Phase 2.

---

## 9. TEST

**105 test, tất cả xanh** (27 test mới Phase 5).

### Phân bổ theo file

| File | Số test | Dựng gì | Trả lời câu hỏi |
|---|---|---|---|
| `MediaServiceTest` | 9 | **Unit thuần** — mock `MinioClient`, không Spring, không container | "Magic byte, hạn mức, tên object có đúng không?" |
| `MessageMediaIntegrationTest` | 11 | Postgres thật, gọi thẳng service | "Attachment/reaction/forward/deleteForMe có đúng từng nhánh không?" |
| `NotificationIntegrationTest` | 7 | Postgres + Redis thật | "Thông báo tạo đúng người chưa, mute có chặn được không?" |

`MediaServiceTest` cố ý là unit test: mọi kiểm tra đều xảy ra **trước** khi chạm storage, nên không
cần container nào. Nó chạy trong 0.3 giây và cũng khẳng định luôn tính chất đó bằng
`verify(minioClient, never()).putObject(any())`.

### Hai test bám sát checklist 5.5 của roadmap

**`chan_file_exe_doi_duoi_thanh_jpg`** — dựng đúng kịch bản tấn công: nội dung là `MZ...` (Windows
executable), tên `anh.jpg`, `Content-Type: image/jpeg`. Xác nhận bị chặn bằng
`FILE_TYPE_NOT_ALLOWED` và không byte nào lên storage.

**`khong_tao_thong_bao_khi_hoi_thoai_dang_bi_mute`** — chỗ này có một vấn đề test đáng ghi lại:
listener chạy bất đồng bộ, nên "không có thông báo" rất dễ là kết luận sai do **kiểm tra quá sớm**.
Giải pháp: gửi thêm một tin ở hội thoại **khác** (không mute) làm **mốc đồng bộ** — khi thông báo của
nó đã xuất hiện, luồng nền chắc chắn đã xử lý xong cả tin trước đó.

```java
messageService.sendMessage(alice, mutedConversation, ...);      // không được sinh thông báo
messageService.sendMessage(alice, anotherConversation, ...);    // mốc đồng bộ
await(() -> notificationRepository.countByUserIdAndReadIsFalse(carol) == 1);
assertThat(notificationRepository.countByUserIdAndReadIsFalse(bob)).isZero();
```

Ngoài ra 4 test kiểm tra thẳng `findNotifiableUserIds()` — tất định, không phụ thuộc luồng nền — phủ
các nhánh: người gửi tự loại mình, mute chặn, mute hết hạn tự bật lại, gửi `null` để bật lại.

### Vì sao không có test upload lên MinIO thật

Cần thêm một container MinIO cho toàn suite chỉ để xác nhận `putObject` hoạt động — mà đó là code của
SDK, không phải code của dự án. Phần **thuộc về dự án** (kiểm tra, đặt tên, dựng URL) đã được phủ đầy
đủ bằng mock. Đánh đổi có ý thức, ghi ở §11.

---

## 10. VƯỚNG MẮC KỸ THUẬT ĐÃ GẶP

### 🟠 1. `web-push` khai dependency ở scope `runtime` nên không biên dịch được

```
[ERROR] package org.apache.http does not exist
[ERROR] cannot access org.jose4j.lang.JoseException
```

`nl.martijndwars:web-push:5.1.2` khai `httpasyncclient` và `jose4j` ở scope **runtime**. Nhưng trình
biên dịch vẫn cần chúng để gọi được `PushService.send()`: kiểu trả về là `org.apache.http.HttpResponse`,
còn mệnh đề `throws` có `JoseException`.

**Cách xử lý**: khai tường minh `httpcore` và `jose4j` ở scope mặc định, **cố định version đúng bằng
bản web-push kéo về** (4.4.16 / 0.7.9) để không lệch phiên bản giữa hai tầng.

Có thể né bằng cách không đọc HTTP status, nhưng như vậy sẽ mất khả năng phát hiện 404/410 — tín hiệu
duy nhất cho biết subscription đã chết. Không đáng đánh đổi.

### 🟡 2. Lombok không chép `@Qualifier` sang tham số constructor (phát hiện từ Phase 4, tái xuất hiện)

Dự án không có `lombok.config`, nên `@RequiredArgsConstructor` **không** chép `@Qualifier` từ field
sang tham số constructor. Với `notificationExecutor` thì không vấn đề vì `@Async("tên")` chọn bean
theo tên, nhưng `presenceScheduler` ở Phase 4 đã phải viết constructor tay — ghi lại ở đây vì đây là
cái bẫy sẽ tái diễn mỗi khi có hai bean cùng kiểu.

### 🟡 3. `MessageResponse` thêm field làm vỡ MapStruct với `unmappedTargetPolicy = ERROR`

Thêm `attachments`/`reactions` vào record khiến build **fail ngay** — đúng như thiết kế của
`ReportingPolicy.ERROR` đặt ra từ Phase 2. Đây là ví dụ tốt cho thấy cấu hình nghiêm ngặt đó có giá
trị: nếu để `WARN`, hai field mới sẽ âm thầm là `null` trên mọi response và chỉ lộ ra khi frontend
báo lỗi.

**Cách xử lý**: đổi sang bản 3 tham số nhận danh sách từ Service, giữ overload 1 tham số cho các chỗ
gọi cũ — nhờ vậy toàn bộ code Phase 3/4 không phải sửa.

### 🟡 4. `Map` không đi qua quy ước `snake_case` của Jackson

Endpoint `/notifications/push/public-key` ban đầu trả `Map.of("enabled", ..., "publicKey", ...)`.
Phát hiện khi rà lại tài liệu: `PropertyNamingStrategy` của Jackson chỉ áp dụng cho **thuộc tính của
POJO**, không cho **khóa của Map** — nên response ra `publicKey` camelCase, lệch với toàn bộ phần còn
lại của API vốn đều `snake_case`.

Đây đúng là loại lỗi không có test nào bắt được (không ai viết assertion cho tên field của một
endpoint phụ) và chỉ lộ ra khi frontend đọc `data.public_key` rồi nhận `undefined`.

**Cách xử lý**: thay bằng record `VapidPublicKeyResponse`. Rút ra: trong dự án này, **response body
luôn phải là record**, không bao giờ là `Map` — kể cả khi chỉ có 2 field.

### 🟡 5. Đọc lại reaction ngay sau khi ghi trong cùng transaction

`reactToMessage()` ghi reaction rồi đọc lại ngay để dựng response. Cùng một transaction, Hibernate
còn đang giữ lệnh INSERT trong hàng đợi nên câu SELECT sẽ không thấy dòng vừa ghi. Phải
`reactionRepository.flush()` trước khi đọc lại.

---

## 11. NỢ KỸ THUẬT

| Món nợ | Ảnh hưởng | Khi nào trả |
|---|---|---|
| **File mồ côi trên MinIO** | Xóa/thu hồi tin nhắn không xóa file vật lý; chuyển tiếp còn làm nhiều bản ghi cùng trỏ một object | Cần job đếm tham chiếu (Phase 8). Cố ý tách khỏi transaction DB vì xóa file không hoàn tác được |
| **Bucket public-read** | Ai có URL đều tải được, kể cả người ngoài hội thoại | Production: bucket private + presigned URL (§6.5) |
| **Chưa sinh thumbnail** | Client tải ảnh gốc 10MB để hiển thị ô 200px; cột `thumbnail_url` đã có nhưng luôn null | Thêm bước resize khi upload ảnh |
| **Không test upload lên MinIO thật** | Lỗi cấu hình MinIO chỉ lộ khi chạy tay | Thêm container MinIO nếu thấy cần (§9) |
| **`MESSAGE_CONTENT_REQUIRED` không kiểm tra ở tầng DTO** | Lỗi trả về ở tầng service thay vì 400 từ bean validation | Chấp nhận — ràng buộc liên-field vốn không thuộc về annotation |
| **Thông báo tạo cho cả người đang mở đúng hội thoại** | Huy hiệu chưa đọc nhảy lên dù người dùng đang nhìn thẳng vào tin đó | Cần theo dõi subscription đang hoạt động (nợ chung với Phase 4) |
| **Push gửi tuần tự từng thiết bị** | Người có 5 thiết bị = 5 lần gọi mạng nối tiếp trên luồng nền | Dùng `sendAsync` nếu đo thấy chậm |
| **`INVALID_EMOJI`, `PUSH_SUBSCRIPTION_INVALID` chưa dùng** | Hai mã lỗi khai mà chưa có chỗ ném | Xóa hoặc dùng khi siết validate emoji |
| **Chưa có `15_API_REFERENCE_MEDIA_NOTIFICATION.md`** | Frontend phải đọc Swagger UI | Viết khi bắt đầu ráp frontend Phase 5 |

---

## 12. CHẠY THỬ BẰNG TAY

```bash
# 0. Bật hạ tầng (MinIO ở cổng 9000, console 9001)
docker compose -f infra/docker-compose.yml up -d

# 1. Chạy app — bucket 'chatsphere-media' được tạo tự động lúc khởi động
./mvnw spring-boot:run

# 2. Đăng nhập 2 user -> $ALICE, $BOB (xem 07_API_REFERENCE_AUTH.md)

# 3. Thử chốt magic byte: tạo file .exe giả rồi đổi đuôi thành .jpg
printf 'MZ\x90\x00\x03\x00\x00\x00' > /tmp/fake.jpg
curl -X POST http://localhost:8080/api/v1/media/upload \
  -H "Authorization: Bearer $ALICE" \
  -F "file=@/tmp/fake.jpg" -F "category=IMAGE"
# -> 415 FILE_TYPE_NOT_ALLOWED  ✅ (đuôi .jpg không cứu được nó)

# 4. Upload ảnh thật -> lấy file_url trong response
curl -X POST http://localhost:8080/api/v1/media/upload \
  -H "Authorization: Bearer $ALICE" \
  -F "file=@anh-that.png" -F "category=IMAGE"

# 5. Gửi tin nhắn kèm ảnh
curl -X POST http://localhost:8080/api/v1/conversations/$CONV/messages \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d '{"type":"IMAGE","attachments":[{
        "file_url":"<file_url ở bước 4>","file_name":"anh-that.png",
        "file_type":"image/png","file_size":20480}]}'

# 6. Bob thả cảm xúc, rồi thả lại đúng emoji đó để gỡ
curl -X PUT http://localhost:8080/api/v1/messages/$MSG/reactions \
  -H "Authorization: Bearer $BOB" -H "Content-Type: application/json" \
  -d '{"emoji":"❤️"}'

# 7. Bob xem thông báo (được tạo bất đồng bộ, gần như tức thì)
curl -H "Authorization: Bearer $BOB" http://localhost:8080/api/v1/notifications
curl -H "Authorization: Bearer $BOB" http://localhost:8080/api/v1/notifications/unread-count

# 8. Bob tắt thông báo hội thoại 8 tiếng, Alice gửi tiếp -> unread-count KHÔNG tăng
curl -X PUT http://localhost:8080/api/v1/conversations/$CONV/mute \
  -H "Authorization: Bearer $BOB" -H "Content-Type: application/json" \
  -d '{"muted_until":"2026-09-07T00:00:00Z"}'

# 9. Bob ẩn 1 tin phía mình -> Bob không thấy nữa, Alice vẫn thấy
curl -X DELETE http://localhost:8080/api/v1/messages/$MSG/for-me \
  -H "Authorization: Bearer $BOB"
```

**Kiểm tra file đã lên thật**: mở MinIO Console `http://localhost:9001`
(`chatsphere_admin` / `minio_dev_password`), vào bucket `chatsphere-media`, xem thư mục theo ngày.

**Bật Web Push** (tùy chọn, cần frontend):
```bash
npx web-push generate-vapid-keys
# rồi thêm vào .env: PUSH_ENABLED=true, VAPID_PUBLIC_KEY=..., VAPID_PRIVATE_KEY=...
```

---

*Hết tài liệu 14_PHASE5_MEDIA_NOTIFICATION_REPORT.md — Phase tiếp theo: WebRTC Signaling
(`03_CODE_ROADMAP.md` §Phase 6).*
