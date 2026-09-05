# API REFERENCE — REAL-TIME (PHASE 4, WEBSOCKET/STOMP + PRESENCE)

**WebSocket endpoint**: `ws://localhost:8080/ws` (dev) · **SockJS fallback**: `http://localhost:8080/ws`
**REST prefix (presence)**: `/api/v1/presence`

Tài liệu này mô tả **hợp đồng kênh real-time** giữa frontend và backend: cách kết nối, xác thực,
danh sách destination, schema từng payload, quy tắc phân quyền subscribe và cách xử lý lỗi. Đi kèm
`12_PHASE4_REALTIME_REPORT.md` (kiến trúc, lý do thiết kế) và `11_API_REFERENCE_CHAT.md` (REST của
module chat — **không thay đổi** ở Phase 4).

> **Điểm mấu chốt cần nắm trước khi đọc tiếp**: WebSocket ở đây **không thay thế REST**. Lịch sử tin
> nhắn, danh sách hội thoại, tạo nhóm... vẫn gọi REST như Phase 3. WebSocket chỉ làm đúng một việc:
> đẩy **thay đổi** tới client mà không cần hỏi lại server. Kể cả gửi tin nhắn, bạn vẫn có thể dùng
> REST — server broadcast y hệt (xem §4.1).

---

## MỤC LỤC

1. [Quy ước chung](#1-quy-ước-chung)
2. [Kết nối & xác thực](#2-kết-nối--xác-thực)
3. [Bảng destination](#3-bảng-destination)
4. [Client → Server](#4-client--server)
   - [`/app/chat.sendMessage`](#41-appchatsendmessage)
   - [`/app/chat.typing`](#42-appchattyping)
   - [`/app/chat.markRead`](#43-appchatmarkread)
5. [Server → Client](#5-server--client)
   - [`/topic/conversation/{id}`](#51-topicconversationid)
   - [`/user/queue/presence`](#52-userqueuepresence)
   - [`/user/queue/errors`](#53-userqueueerrors)
6. [Phân quyền SUBSCRIBE](#6-phân-quyền-subscribe)
7. [REST bổ trợ — Presence](#7-rest-bổ-trợ--presence)
8. [Mã client tham khảo](#8-mã-client-tham-khảo)
9. [Kịch bản test bằng tay](#9-kịch-bản-test-bằng-tay)
10. [Giới hạn đã biết](#10-giới-hạn-đã-biết)

---

## 1. QUY ƯỚC CHUNG

### 1.1. Định dạng JSON — giống hệt REST

Payload trên WebSocket dùng **cùng bộ chuyển đổi JSON với REST**, nên mọi quy ước đã quen ở
`07_API_REFERENCE_AUTH.md` §1 vẫn đúng:

| Quy ước | Giá trị |
|---|---|
| Tên field | `snake_case` (`conversation_id`, `last_read_message_id`) |
| Field `null` | **Bị loại khỏi JSON**, không gửi `"field": null` |
| Thời gian | Chuỗi ISO-8601 UTC (`"2026-09-06T10:15:30Z"`) |
| UUID | Chuỗi có dấu gạch nối |
| Enum | Chuỗi viết hoa (`"TEXT"`, `"ONLINE"`) |

Ví dụ một frame `MessageResponse` thật trên dây (đã bỏ `reply_to_message_id` vì null):

```json
{
  "id": "3f1c...",
  "conversation_id": "9ab2...",
  "sender": {
    "id": "11ee...",
    "username": "alice",
    "display_name": "Alice Nguyen",
    "avatar_url": "http://localhost:9000/chatsphere-media/2026/09/06/xxx.png",
    "bio": "hi"
  },
  "type": "TEXT",
  "content": "xin chao",
  "status": "SENT",
  "edited": false,
  "attachments": [],
  "reactions": [],
  "created_at": "2026-09-06T10:15:30Z"
}
```

> `attachments` và `reactions` (thêm ở Phase 5) **luôn là mảng**, không bao giờ null — client duyệt
> thẳng không cần kiểm tra.

### 1.2. Ba tiền tố destination và ý nghĩa

| Tiền tố | Hướng | Ý nghĩa |
|---|---|---|
| `/app/...` | Client **gửi** | Đi tới `@MessageMapping` phía server |
| `/topic/...` | Client **nghe** | Nhiều người cùng nhận (mọi thành viên 1 hội thoại) |
| `/user/queue/...` | Client **nghe** | Chỉ riêng mình. Client subscribe `/user/queue/presence`, Spring tự dịch sang phiên cụ thể — **không** tự chèn userId vào đường dẫn |

---

## 2. KẾT NỐI & XÁC THỰC

### 2.1. Vì sao JWT không nằm ở HTTP header

WebSocket chỉ có **đúng một** request HTTP: cái bắt tay nâng cấp giao thức. Sau đó mọi frame đi trên
kết nối TCP đã mở, không còn HTTP header nào nữa. Tệ hơn, trình duyệt **không cho phép** gắn header
tùy ý vào `new WebSocket(...)`.

Vì vậy JWT được gửi trong **native header của frame STOMP `CONNECT`** — frame đầu tiên client gửi
ngay sau khi kết nối — và được xác thực ở `ChannelInterceptor`, không phải filter HTTP.

### 2.2. Frame CONNECT

```
CONNECT
accept-version:1.2
Authorization:Bearer <access_token>

^@
```

Access token lấy từ `POST /api/v1/auth/login` (xem `07_API_REFERENCE_AUTH.md`) — **cùng một token**
dùng cho REST, không có token riêng cho WebSocket.

### 2.3. Kết quả

| Trường hợp | Phản hồi |
|---|---|
| Token hợp lệ | `CONNECTED` frame, phiên gắn `Principal` = userId cho tới khi ngắt |
| Thiếu header `Authorization` | `ERROR` frame `"Kết nối WebSocket thiếu token hoặc token không hợp lệ"`, **đóng kết nối** |
| Token sai chữ ký / hết hạn | Như trên |

> **Khác REST một cách có chủ ý**: ở REST, token hỏng chỉ khiến request thành "ẩn danh" rồi để
> `SecurityConfig` quyết định. Ở đây phải chặn ngay — một phiên STOMP không có `Principal` sẽ sống
> tới khi client tự đóng, và mọi frame sau đó đều vô nghĩa.

### 2.4. Access token hết hạn giữa chừng

Token chỉ được kiểm tra **một lần tại CONNECT**. Phiên đang mở **không** bị đá ra khi token hết hạn
(15 phút mặc định). Đây là hành vi có chủ ý — xem §10.

---

## 3. BẢNG DESTINATION

| Destination | Hướng | Payload | Use case |
|---|---|---|---|
| `/app/chat.sendMessage` | C→S | `WsSendMessageRequest` | UC-18 gửi tin nhắn |
| `/app/chat.typing` | C→S | `TypingRequest` | UC-24 đang soạn tin |
| `/app/chat.markRead` | C→S | `MarkReadRequest` | UC-24 đã đọc |
| `/topic/conversation/{id}` | S→C | `MessageResponse` \| `TypingEvent` \| `ReadReceiptEvent` | Mọi sự kiện của 1 hội thoại |
| `/user/queue/presence` | S→C | `PresenceEvent` | Bạn bè online/offline |
| `/user/queue/errors` | S→C | `ApiResponse<void>` | Lỗi nghiệp vụ của frame vừa gửi |
| `/user/queue/notifications` | S→C | `NotificationResponse` | Thông báo (Phase 5) |

---

## 4. CLIENT → SERVER

### 4.1. `/app/chat.sendMessage`

Gửi tin nhắn. Gọi lại **đúng** `MessageService.sendMessage()` mà REST dùng — mọi quy tắc nghiệp vụ
(phải là thành viên, không bị chặn, reply đúng hội thoại) giữ nguyên.

**Body** — `WsSendMessageRequest`:

| Field | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `conversation_id` | UUID | ✅ | REST lấy từ path, STOMP không có path nên đưa vào body |
| `type` | enum | ✅ | `TEXT` \| `IMAGE` \| `FILE` \| `VOICE` |
| `content` | string ≤5000 | ⚠️ | Bắt buộc **nếu không có** `attachments` |
| `reply_to_message_id` | UUID | ❌ | Phải thuộc cùng hội thoại |
| `attachments` | array ≤10 | ⚠️ | Bắt buộc với `IMAGE`/`FILE`/`VOICE` (Phase 5) |

```json
{ "conversation_id": "9ab2...", "type": "TEXT", "content": "Chao ca nha" }
```

**Kết quả**: server **không** trả gì trực tiếp cho người gửi. Tin nhắn được phát tới
`/topic/conversation/{id}` sau khi transaction commit — người gửi cũng nhận được frame đó (xem §5.1).

> **Gửi qua REST hay WebSocket đều được.** `POST /api/v1/conversations/{id}/messages` phát ra **cùng
> một frame** trên cùng destination. Việc broadcast không nằm ở controller mà ở một listener nghe
> sự kiện `MessageSentEvent`, nên không đường nào có thể "quên" phát sóng.

**Lỗi có thể gặp** (trả về `/user/queue/errors`): `CONVERSATION_NOT_FOUND`, `NOT_CONVERSATION_MEMBER`,
`USER_BLOCKED`, `MESSAGE_NOT_IN_CONVERSATION`, `MESSAGE_CONTENT_REQUIRED`, `ATTACHMENT_REQUIRED`.

---

### 4.2. `/app/chat.typing`

Báo bắt đầu / dừng soạn tin. **Không chạm database** — dữ liệu chỉ có nghĩa trong vài giây.

**Body** — `TypingRequest`:

| Field | Kiểu | Bắt buộc |
|---|---|---|
| `conversation_id` | UUID | ✅ |
| `typing` | boolean | ✅ |

```json
{ "conversation_id": "9ab2...", "typing": true }
```

> **Vì sao có cờ `typing: false` chứ không để client tự hết hạn sau vài giây?** Người gõ xong rồi xóa
> sạch chữ mà vẫn hiện "đang soạn tin..." thêm mấy giây là sai sự thật. Client vẫn **nên** đặt thêm
> timeout an toàn (~5s) phòng khi frame `false` bị mất.

---

### 4.3. `/app/chat.markRead`

Dời con trỏ "đã đọc" (UC-24), đồng thời làm `unread_count` của hội thoại về 0.

**Body** — `MarkReadRequest`:

| Field | Kiểu | Bắt buộc |
|---|---|---|
| `conversation_id` | UUID | ✅ |
| `message_id` | UUID | ✅ |

```json
{ "conversation_id": "9ab2...", "message_id": "3f1c..." }
```

> **Con trỏ chỉ tiến, không lùi.** Gửi lại một `message_id` cũ hơn con trỏ hiện tại **không** phải
> lỗi — server im lặng bỏ qua và **không** phát biên nhận. Nhờ vậy client cứ gửi thoải mái khi
> người dùng cuộn qua lại mà không sợ `unread_count` tự dưng tăng lại.

---

## 5. SERVER → CLIENT

### 5.1. `/topic/conversation/{id}`

Một destination duy nhất mang **ba** loại payload. Client phân biệt bằng field có mặt:

| Nhận diện | Là gì | Xử lý |
|---|---|---|
| Có `sender` | `MessageResponse` — tin mới **hoặc** tin thu hồi | `status == "RECALLED"` → thay tin cũ cùng `id`; ngược lại → chèn tin mới |
| Có `typing` | `TypingEvent` | Hiện/ẩn "Đang soạn tin..." |
| Có `last_read_message_id` | `ReadReceiptEvent` | Cập nhật dấu "Đã xem" |

**`TypingEvent`**
```json
{ "conversation_id": "9ab2...", "user_id": "11ee...", "typing": true }
```

**`ReadReceiptEvent`**
```json
{
  "conversation_id": "9ab2...",
  "user_id": "11ee...",
  "last_read_message_id": "3f1c...",
  "read_at": "2026-09-06T10:15:30Z"
}
```

> ⚠️ **Người gửi cũng nhận lại sự kiện của chính mình.** Server broadcast lên topic chung, không loại
> trừ ai. Client phải tự bỏ qua `user_id == mình` với typing. Với `MessageResponse` thì **nên giữ**:
> nó cho phép mọi thiết bị của cùng người dùng đồng bộ từ một nguồn sự thật duy nhất, và cho bạn
> `id` thật do server sinh thay vì id tạm phía client.
>
> Đây là đánh đổi có chủ ý: loại trừ người gửi ở server đòi hỏi gửi riêng từng thành viên (N frame
> thay vì 1) chỉ để tiết kiệm một dòng `if` phía client.

**Thu hồi tin nhắn** dùng lại đúng schema `MessageResponse`, với `status: "RECALLED"` và **không có**
field `content` (đã bị loại vì null):

```json
{ "id": "3f1c...", "conversation_id": "9ab2...", "sender": {...},
  "type": "TEXT", "status": "RECALLED", "edited": false,
  "attachments": [], "reactions": [], "created_at": "2026-09-06T10:15:30Z" }
```

---

### 5.2. `/user/queue/presence`

Bạn bè vừa online/offline. Client subscribe đúng chuỗi `"/user/queue/presence"` — **không** chèn
userId.

```json
{ "user_id": "11ee...", "status": "ONLINE", "at": "2026-09-06T10:15:30Z" }
```

| Field | Ghi chú |
|---|---|
| `status` | `ONLINE` \| `OFFLINE` |
| `at` | Thời điểm đổi trạng thái. Với `OFFLINE` đây chính là "hoạt động lần cuối" để hiển thị "Hoạt động 5 phút trước" |

**Ba quy tắc quan trọng:**

1. **Chỉ bạn bè nhận được.** Người lạ không bao giờ thấy trạng thái của bạn qua kênh này.
2. **Tôn trọng `online_visibility`** (UC-13): đặt `NOBODY` thì server không phát gì cả.
3. **Offline có debounce ~10 giây.** Mất wifi vài giây, đổi wifi sang 4G, hay chỉ là F5 đều tạo ra
   một cặp disconnect/connect cách nhau 1-2 giây. Server chờ 10s rồi kiểm tra lại; nếu người đó đã
   kết nối lại thì sự kiện `OFFLINE` **bị hủy**, bạn bè không thấy gì bất thường.
4. **Nhiều tab = vẫn online.** Server đếm số phiên đang mở, `OFFLINE` chỉ phát khi phiên **cuối cùng**
   đóng — đóng 1 tab trong khi điện thoại vẫn kết nối thì không đổi trạng thái.

> Sự kiện chỉ mang **thay đổi**. Trạng thái hiện tại lúc mới mở app phải lấy qua REST — xem §7.

---

### 5.3. `/user/queue/errors`

Lỗi nghiệp vụ của frame bạn vừa gửi, dùng lại **đúng phong bì `ApiResponse` của REST** để client chỉ
phải hiểu một định dạng lỗi:

```json
{
  "success": false,
  "data": null,
  "error": { "code": "NOT_CONVERSATION_MEMBER", "message": "Bạn không phải thành viên của cuộc trò chuyện này" },
  "timestamp": "2026-09-06T10:15:30Z"
}
```

> **Lỗi nghiệp vụ KHÔNG đóng kết nối** (khác lỗi xác thực ở `CONNECT`). Gõ nhầm vào một hội thoại
> vừa bị xóa mà mất luôn cả phiên real-time thì quá nặng tay.

---

## 6. PHÂN QUYỀN SUBSCRIBE

Xác thực (*anh là ai*) không thay được phân quyền (*anh được xem gì*). Mọi frame `SUBSCRIBE` đều bị
kiểm tra:

| Destination | Quy tắc |
|---|---|
| `/topic/conversation/{id}` | Phải là **participant còn active** của hội thoại đó. Không thì `ERROR` + đóng phiên |
| `/user/**` | Luôn cho phép — Spring tự dịch sang phiên của chính người subscribe, không cách nào nghe ké queue người khác |
| Bất kỳ destination nào khác | **Từ chối** |

Không có chốt này, bất kỳ ai đăng nhập hợp lệ đều có thể subscribe `/topic/conversation/<uuid đoán được>`
và đọc trộm toàn bộ tin nhắn real-time của người lạ.

---

## 7. REST BỔ TRỢ — PRESENCE

WebSocket chỉ phát **thay đổi**. Client vừa mở trang mà chỉ nghe WebSocket sẽ không biết ai đang
online cho tới khi có người đầu tiên đổi trạng thái — có thể vài phút sau. Mẫu chuẩn: gọi REST một
lần lấy trạng thái nền, rồi để WebSocket cập nhật dần từ đó.

### 7.1. `GET /api/v1/presence/friends`

Danh sách id bạn bè đang online. Gọi **một lần** khi mở app.

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/presence/friends
```

```json
{ "success": true, "data": ["11ee...", "22ff..."], "error": null, "timestamp": "..." }
```

### 7.2. `GET /api/v1/presence/{userId}`

Một người có đang online không.

```json
{ "success": true, "data": true, "error": null, "timestamp": "..." }
```

> Trả `false` thay vì 403 khi không đủ quyền xem (`online_visibility`). Phân biệt "đang offline" với
> "không cho bạn xem" chính là thứ mà cài đặt riêng tư cố tình che đi — cùng nguyên tắc với mã lỗi
> `USER_BLOCKED` dùng chung cho cả hai chiều chặn ở Phase 2.

---

## 8. MÃ CLIENT THAM KHẢO

Dùng `@stomp/stompjs` + `sockjs-client`:

```ts
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const client = new Client({
  // SockJS cần URL http(s), không phải ws(s). Bỏ webSocketFactory nếu dùng WebSocket thuần.
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
  connectHeaders: { Authorization: `Bearer ${accessToken}` },
  reconnectDelay: 5000,

  onConnect: () => {
    // 1. Sự kiện của hội thoại đang mở
    client.subscribe(`/topic/conversation/${conversationId}`, (frame) => {
      const payload = JSON.parse(frame.body);
      if (payload.sender) onMessage(payload);              // tin mới / thu hồi
      else if ('typing' in payload) onTyping(payload);      // đang soạn tin
      else if (payload.last_read_message_id) onRead(payload); // đã xem
    });

    // 2. Bạn bè online/offline — KHÔNG chèn userId vào đường dẫn
    client.subscribe('/user/queue/presence', (f) => onPresence(JSON.parse(f.body)));

    // 3. Lỗi nghiệp vụ của chính mình
    client.subscribe('/user/queue/errors', (f) => toastError(JSON.parse(f.body).error));
  },

  // Token hỏng/hết hạn -> server gửi ERROR frame rồi đóng. Làm mới token trước khi thử lại.
  onStompError: (frame) => console.error('STOMP error:', frame.headers['message']),
});

client.activate();

// Gửi tin nhắn
client.publish({
  destination: '/app/chat.sendMessage',
  body: JSON.stringify({ conversation_id: conversationId, type: 'TEXT', content: 'Chao' }),
});
```

**Ba lỗi hay gặp khi ráp frontend:**

1. **Subscribe trước khi `onConnect` chạy** → frame bị bỏ. Luôn subscribe bên trong `onConnect`, và
   subscribe **lại** sau mỗi lần tự kết nối lại.
2. **Chèn userId vào `/user/queue/...`** → không nhận được gì. Spring tự làm việc đó.
3. **Quên hủy subscribe khi đổi hội thoại** → nhận tin của hội thoại cũ. Giữ `subscription.unsubscribe()`
   trong hàm cleanup của effect.

---

## 9. KỊCH BẢN TEST BẰNG TAY

Đúng mục "Kiểm tra hoàn thành Phase 4" của `03_CODE_ROADMAP.md`:

```bash
# 0. Bật hạ tầng + chạy app
docker compose -f infra/docker-compose.yml up -d
./mvnw spring-boot:run

# 1. Đăng ký + xác thực + đăng nhập 2 tài khoản (xem 07_API_REFERENCE_AUTH.md)
#    -> lấy 2 access_token, gọi chúng là $ALICE và $BOB

# 2. Alice tạo hội thoại 1-1 với Bob -> lấy conversation_id
curl -X POST http://localhost:8080/api/v1/conversations/direct \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d '{"user_id":"<BOB_USER_ID>"}'

# 3. Kiểm tra presence nền
curl -H "Authorization: Bearer $ALICE" http://localhost:8080/api/v1/presence/friends
```

Sau đó mở **2 trình duyệt** (hoặc 1 thường + 1 ẩn danh), đăng nhập 2 tài khoản, và xác nhận:

| Việc làm | Kết quả mong đợi |
|---|---|
| Alice gửi tin | Bob thấy **ngay**, không cần F5 |
| Alice gõ vào ô nhập | Bob thấy "Đang soạn tin..." |
| Bob mở hội thoại | Alice thấy "Đã xem"; huy hiệu chưa đọc của Bob về 0 |
| Alice đóng tab | Sau ~10 giây, chấm xanh của Alice chuyển xám ở màn hình Bob |
| Alice mở lại trong vòng 10 giây | Chấm xanh **không** nhấp nháy — debounce đã hủy sự kiện offline |
| Alice mở thêm tab thứ 2 rồi đóng tab 1 | Vẫn xanh — còn phiên đang mở |

Không có frontend thì dùng **Postman WebSocket Request** (`ws://localhost:8080/ws`, protocol STOMP)
hoặc chạy `ChatWebSocketIntegrationTest` — nó dựng 2 client STOMP thật và kiểm tra đúng các luồng trên.

---

## 10. GIỚI HẠN ĐÃ BIẾT

| Giới hạn | Hệ quả thực tế | Hướng xử lý |
|---|---|---|
| **Simple broker trong RAM** | Chạy 2 instance sau load balancer: người nối vào instance A **không** nhận được tin do instance B phát | Đổi sang `enableStompBrokerRelay` + RabbitMQ/ActiveMQ khi scale ngang (`04_PRODUCTION_DEPLOYMENT.md`) |
| **Token chỉ kiểm tra tại CONNECT** | Phiên đang mở sống tiếp dù access token hết hạn (15 phút) | Chấp nhận được: kết nối đứt là phải CONNECT lại với token mới. Muốn chặt hơn thì thêm interceptor kiểm tra hạn ở mỗi frame SEND |
| **Không có ACK/retry ở tầng ứng dụng** | Frame mất khi mạng chập chờn thì mất luôn | Dữ liệu đã nằm trong DB — client gọi lại `GET /messages` sau khi reconnect là đủ |
| **Typing broadcast cho cả người gửi** | Client phải tự lọc `user_id == mình` | Có chủ ý (§5.1) |
| **Chưa có "đang mở hội thoại nào"** | Thông báo Phase 5 được tạo cho cả người đang mở đúng hội thoại đó | Cần theo dõi subscription đang hoạt động — hoãn sang sau |

---

*Hết tài liệu 13_API_REFERENCE_REALTIME.md — xem `12_PHASE4_REALTIME_REPORT.md` để hiểu vì sao từng
quyết định thiết kế ở trên được chọn.*
