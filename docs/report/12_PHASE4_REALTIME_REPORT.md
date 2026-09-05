# BÁO CÁO PHASE 4 — REAL-TIME CHAT (WEBSOCKET/STOMP + PRESENCE)

**Dự án**: ChatSphere backend · **Stack**: Spring Boot 4.1.1, Java 21, PostgreSQL 16, Redis 7
**Trạng thái**: hoàn thành phần backend, 78/78 test xanh (8 test mới của Phase 4, cộng 70 test Phase 1-3 không bị vỡ)
**Tài liệu liên quan**: `01_SYSTEM_DESIGN.md` (§9.1 vòng đời kết nối), `03_CODE_ROADMAP.md` (§Phase 4),
`10_PHASE3_CHAT_REPORT.md` (tầng service được dùng lại nguyên vẹn), `13_API_REFERENCE_REALTIME.md`

---

## MỤC LỤC

1. [Phạm vi đã làm](#1-phạm-vi-đã-làm)
2. [Bản đồ file](#2-bản-đồ-file)
3. [Kiến trúc tầng real-time](#3-kiến-trúc-tầng-real-time)
4. [Bảng destination](#4-bảng-destination)
5. [Luồng chi tiết](#5-luồng-chi-tiết)
6. [Lý thuyết nền](#6-lý-thuyết-nền)
7. [Bản đồ mã lỗi](#7-bản-đồ-mã-lỗi)
8. [Bảo mật & riêng tư](#8-bảo-mật--riêng-tư)
9. [Test](#9-test)
10. [Quyết định lệch roadmap](#10-quyết-định-lệch-roadmap)
11. [Nợ kỹ thuật](#11-nợ-kỹ-thuật)
12. [Chạy thử bằng tay](#12-chạy-thử-bằng-tay)

---

## 1. PHẠM VI ĐÃ LÀM

| Mục roadmap | Nội dung | Trạng thái |
|---|---|---|
| 4.1 | `WebSocketConfig` + `WebSocketAuthInterceptor` + đăng ký vào inbound channel | ✅ |
| 4.2 | `PresenceService` (Redis), `WebSocketEventListener`, phát online/offline cho bạn bè | ✅ |
| 4.3 | `ChatWebSocketController`: `sendMessage`, `typing`, `markRead` | ✅ |
| 4.4 | Frontend: `websocketClient.ts`, các hook, UI | ⏸ **Không thuộc repo này** (backend-only) |
| 4.5 | Integration test bằng `WebSocketStompClient`, 2 client giả lập | ✅ |

**Use case**: UC-18 (real-time hóa), UC-24 (typing + read receipt), presence.

**Quy mô**: 20 file Java mới (main) + 1 file test, **0 migration** — Phase 4 không thêm bảng nào.
Cột `conversation_participants.last_read_message_id` đã có từ Phase 3; presence sống hoàn toàn trong
Redis.

**Nguyên tắc xuyên suốt**: Phase 4 là **một lớp vỏ mỏng**. Không một dòng business rule nào được
viết lại — `ChatWebSocketController` gọi đúng `MessageService` mà REST đã dùng và đã được test kỹ ở
Phase 3. Đây chính là phần thưởng của việc roadmap tách Phase 3 khỏi Phase 4.

---

## 2. BẢN ĐỒ FILE

```
com.chatsphere
│
├── config/
│   └── WebSocketConfig.java              @EnableWebSocketMessageBroker; /ws (thuần + SockJS),
│                                         simple broker /topic + /queue, prefix /app, /user
│
├── auth/security/
│   ├── WebSocketAuthInterceptor.java     ChannelInterceptor: CONNECT -> xác thực JWT
│   │                                     SUBSCRIBE -> phân quyền theo destination
│   └── StompPrincipal.java               record(UUID userId); getName() = userId.toString()
│
├── common/
│   └── WsDestinations.java               MỘT nơi định nghĩa mọi chuỗi destination + parse topic
│
├── chat/
│   ├── controller/
│   │   └── ChatWebSocketController.java  3 @MessageMapping + 2 @MessageExceptionHandler
│   ├── dto/
│   │   ├── WsSendMessageRequest.java     = SendMessageRequest + conversationId
│   │   ├── TypingRequest.java            conversationId + cờ typing
│   │   ├── TypingEvent.java              phát lên topic, KHÔNG lưu DB
│   │   ├── MarkReadRequest.java          conversationId + messageId
│   │   └── ReadReceiptEvent.java         biên nhận "đã xem"
│   ├── event/
│   │   ├── MessageSentEvent.java         3 sự kiện nội bộ tách service khỏi tầng vận chuyển
│   │   ├── MessageRecalledEvent.java
│   │   └── MessageReadEvent.java
│   └── service/
│       └── ChatRealtimeBroadcaster.java  @TransactionalEventListener(AFTER_COMMIT) -> convertAndSend
│
└── presence/
    ├── PresenceService.java              Redis thuần: đếm phiên, KHÔNG biết gì về bạn bè/JPA
    ├── PresenceBroadcaster.java          JPA + messaging: ai được thấy ai, rồi gửi
    ├── WebSocketEventListener.java       SessionConnected/SessionDisconnect + debounce 10s
    ├── PresenceController.java           2 endpoint REST lấy trạng thái nền
    ├── PresenceEvent.java
    └── PresenceStatus.java               ONLINE | OFFLINE
```

**File Phase trước bị sửa:**

| File | Thay đổi | Vì sao |
|---|---|---|
| `SecurityConfig` | thêm `/ws/**` vào `PUBLIC_ENDPOINTS` | Trình duyệt không cho gắn header vào handshake; auth thật ở frame CONNECT |
| `AsyncConfig` | thêm bean `presenceScheduler` | Debounce offline cần `TaskScheduler` riêng, không dùng chung `mailExecutor` |
| `MessageService` | thêm `markRead()`, `assertParticipant()`, phát 3 event | UC-24 + chốt quyền cho typing |
| `FriendshipRepository` | thêm `findFriendIdsOf()` | Phát presence phải gửi cho **tất cả** bạn bè, không phân trang |
| `ErrorCode` | thêm 2 mã WebSocket | |

---

## 3. KIẾN TRÚC TẦNG REAL-TIME

```
                      ┌──────────────── Client (trình duyệt) ────────────────┐
                      │  STOMP over WebSocket, JWT ở native header CONNECT   │
                      └───────────────┬──────────────────────────────────────┘
                                      │
                          ╔═══════════▼═══════════╗
                          ║  clientInboundChannel ║
                          ║  WebSocketAuthInterceptor
                          ║   • CONNECT  -> xác thực JWT, gắn StompPrincipal
                          ║   • SUBSCRIBE-> kiểm tra participant
                          ╚═══════════┬═══════════╝
                                      │
                  ┌───────────────────▼───────────────────┐
                  │       ChatWebSocketController          │  (vỏ mỏng, không có business rule)
                  └───────────────────┬───────────────────┘
                                      │  gọi lại nguyên vẹn
                  ┌───────────────────▼───────────────────┐
                  │   MessageService (Phase 3, @Transactional)
                  │   ...publishEvent(MessageSentEvent)    │
                  └───────────────────┬───────────────────┘
                                      │  COMMIT xong mới bắn
                  ┌───────────────────▼───────────────────┐
                  │  ChatRealtimeBroadcaster (AFTER_COMMIT)│
                  └───────────────────┬───────────────────┘
                                      │
                          simpMessagingTemplate.convertAndSend
                                      │
                            /topic/conversation/{id}
```

Điểm cần thấy trong sơ đồ này: **đường REST của Phase 3 cắm vào đúng chỗ `MessageService`**, nên nó
cũng đi qua `MessageSentEvent` → broadcaster → cùng một topic. Không có hai đường phát sóng song song.

### Presence — hai class, hai trách nhiệm

```
PresenceService          Redis thuần. Key presence:sessions:{userId} -> SET<sessionId>, TTL 12h.
   ↑ không biết JPA      addSession() trả "vừa online?", removeSession() trả "hết phiên?"
   │
WebSocketEventListener   Nghe SessionConnected/Disconnect. Hết phiên -> hẹn kiểm tra lại sau 10s.
   │
   ↓ gọi
PresenceBroadcaster      JPA: bạn bè là ai, online_visibility ra sao. Rồi convertAndSendToUser().
```

Tách như vậy để `PresenceService` test được mà không cần JPA, và để logic riêng tư nằm gọn một chỗ.

---

## 4. BẢNG DESTINATION

| Destination | Hướng | Payload | Xử lý ở đâu |
|---|---|---|---|
| `/app/chat.sendMessage` | C→S | `WsSendMessageRequest` | `ChatWebSocketController.sendMessage` |
| `/app/chat.typing` | C→S | `TypingRequest` | `ChatWebSocketController.typing` |
| `/app/chat.markRead` | C→S | `MarkReadRequest` | `ChatWebSocketController.markRead` |
| `/topic/conversation/{id}` | S→C | `MessageResponse` \| `TypingEvent` \| `ReadReceiptEvent` | `ChatRealtimeBroadcaster` + controller (typing) |
| `/user/queue/presence` | S→C | `PresenceEvent` | `PresenceBroadcaster` |
| `/user/queue/errors` | S→C | `ApiResponse<void>` | `@MessageExceptionHandler` |
| `GET /api/v1/presence/friends` | REST | `Set<UUID>` | `PresenceController` |
| `GET /api/v1/presence/{userId}` | REST | `boolean` | `PresenceController` |

Schema chi tiết từng payload: `13_API_REFERENCE_REALTIME.md`.

---

## 5. LUỒNG CHI TIẾT

### 5.1. Xác thực tại `CONNECT` — vì sao không dùng lại được `JwtAuthenticationFilter`

WebSocket chỉ có **đúng một** request HTTP: cái bắt tay nâng cấp giao thức. Sau đó mọi frame đi trên
kết nối TCP đã mở — không còn HTTP header nào cho filter đọc. Tệ hơn nữa, trình duyệt **không cho
phép** gắn header tùy ý vào `new WebSocket(...)`, nên kể cả muốn cũng không gửi được
`Authorization` ở handshake.

Giải pháp chuẩn của STOMP: đặt JWT vào **native header của frame `CONNECT`** và xác thực ở
`ChannelInterceptor` — cùng `JwtTokenProvider.parse()` mà REST dùng, vì hàm đó cố ý không phụ thuộc
`HttpServletRequest` (đã ghi chú từ Phase 1).

Hệ quả kéo theo: `/ws/**` phải `permitAll` ở `SecurityConfig`. Nghe như một lỗ hổng nhưng không phải —
handshake được mở, còn phiên nào không CONNECT hợp lệ thì bị đóng ngay và không subscribe hay gửi
được gì.

**Khác REST một cách có chủ ý**: token hỏng ở REST chỉ khiến request thành "ẩn danh" rồi để
`SecurityConfig` quyết định. Ở WebSocket phải chặn dứt khoát — một phiên STOMP không có `Principal`
sẽ sống tới khi client tự đóng, và mọi frame sau đó đều vô nghĩa.

### 5.2. Phân quyền `SUBSCRIBE` — chốt chặn roadmap không nhắc tới

Roadmap chỉ yêu cầu xác thực ở `CONNECT`. Nhưng xác thực (*anh là ai*) không thay được phân quyền
(*anh được xem gì*): nếu dừng ở đó, **bất kỳ ai đăng nhập hợp lệ** đều có thể

```
SUBSCRIBE destination:/topic/conversation/<uuid của người khác>
```

và đọc trộm toàn bộ tin nhắn real-time của họ. REST đã chặn bằng `getActiveParticipantOrThrow()` ở
mọi endpoint, nhưng kênh WebSocket là một cửa hoàn toàn mới và ban đầu không có chốt nào.

Vì vậy interceptor xử lý luôn `StompCommand.SUBSCRIBE`:

| Destination | Quy tắc |
|---|---|
| `/topic/conversation/{id}` | Phải là participant còn active → ngược lại `ERROR` + đóng phiên |
| `/user/**` | Cho phép — Spring tự dịch sang phiên của chính người subscribe |
| Khác | Từ chối |

Test `khong_the_subscribe_topic_cua_hoi_thoai_minh_khong_tham_gia` phủ đúng lỗ hổng này.

### 5.3. Gửi tin nhắn — phát sóng sau khi commit, không phát trong service

Đây là quyết định thiết kế lớn nhất của Phase 4, và nó **lệch so với roadmap** (xem §10).

Nếu phát frame ngay trong `sendMessage()`, một lỗi ở cuối transaction (vi phạm ràng buộc, mất kết
nối DB) sẽ rollback tin nhắn — nhưng frame WebSocket thì **không rollback được**, nó đã bay tới
trình duyệt rồi. Người dùng nhìn thấy một tin nhắn không hề tồn tại trong DB và biến mất khi F5.

Chờ commit xong mới phát thì chỉ còn rủi ro ngược lại, nhẹ hơn nhiều: tin có trong DB nhưng frame lỡ
mất — người dùng F5 hoặc mở lại là thấy.

```java
@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
public void onMessageSent(MessageSentEvent event) { ... }
```

`fallbackExecution = true` phòng trường hợp về sau có luồng gọi service ngoài transaction (job nền,
test gọi thẳng) — không có nó, listener sẽ **im lặng không chạy**, kiểu hỏng khó phát hiện nhất.

### 5.4. `markRead` — con trỏ chỉ tiến, không lùi

Client gửi biên nhận rất tùy hứng: tab cũ mở từ hôm qua, người dùng cuộn ngược lên đọc lại tin cũ,
mạng gửi lặp frame. Nếu cho phép con trỏ lùi thì `unread_count` sẽ **tự dưng tăng lại** — người dùng
thấy huy hiệu "chưa đọc" trên hội thoại mình vừa đọc xong.

```java
boolean movesForward = current == null || current.getCreatedAt().isBefore(message.getCreatedAt());
if (!movesForward) {
    return new ReadReceiptEvent(...current...);   // không lỗi, cũng không phát sự kiện
}
```

So sánh bằng `createdAt` (không phải thời điểm gọi API) vì đó chính là trục mà
`countUnreadByConversationIds` dùng để đếm. Trả về biên nhận hiện tại thay vì ném lỗi: đây không
phải lỗi của client, chỉ là thao tác không có gì để làm.

### 5.5. Presence — debounce 10 giây và đếm phiên

**Vì sao đếm `Set<sessionId>` chứ không phải cờ boolean "online"?** Một người thường mở nhiều tab và
nhiều thiết bị. Với cờ boolean, đóng **một** tab sẽ đặt cờ về offline dù điện thoại vẫn đang kết nối —
bạn bè thấy họ "offline" trong khi họ vẫn đang chat. Đếm phiên thì "offline" chỉ xảy ra khi phiên
**cuối cùng** đóng.

**Vì sao debounce ~10 giây?** Mất sóng wifi vài giây, chuyển wifi sang 4G, hay chỉ là người dùng bấm
F5 — tất cả đều tạo ra một cặp disconnect/connect cách nhau 1-2 giây. Báo offline ngay khiến chấm
trạng thái nhấp nháy xanh-xám liên tục. Chờ 10 giây rồi **kiểm tra lại**: nếu người đó đã kết nối
lại, sự kiện offline bị hủy.

```java
presenceScheduler.schedule(() -> {
    if (presenceService.isOnline(userId)) return;   // đã quay lại -> hủy
    presenceBroadcaster.broadcast(userId, OFFLINE);
}, Instant.now().plus(OFFLINE_DEBOUNCE));
```

**TTL 12 giờ trên key Redis** là lưới an toàn: tiến trình chết đột ngột (`kill -9`, mất điện) thì sự
kiện DISCONNECT không bao giờ chạy và key sẽ nằm lại vĩnh viễn — người đó "online" mãi mãi. TTL được
gia hạn mỗi lần có phiên mới nên phiên sống thật không bao giờ hết hạn oan.

---

## 6. LÝ THUYẾT NỀN

### 6.1. Simple broker — biết trước giới hạn thay vì phát hiện lúc production

`enableSimpleBroker()` giữ toàn bộ phiên và đăng ký topic trong **RAM của đúng tiến trình này**.
Chạy 2 instance sau load balancer thì người nối vào instance A sẽ **không** nhận được tin do instance
B phát — và triệu chứng là "thỉnh thoảng tin nhắn không hiện", cực khó chẩn đoán nếu không biết trước.

Với 1 instance ở phạm vi học tập, simple broker là lựa chọn đúng: không thêm hạ tầng, không thêm
điểm hỏng. Muốn scale ngang phải đổi sang `enableStompBrokerRelay` + RabbitMQ/ActiveMQ.

### 6.2. Vì sao `StompPrincipal.getName()` trả về userId

Spring dùng `Principal.getName()` làm khóa cho destination riêng người dùng. Nhờ `getName()` trả
userId dạng chuỗi mà:

```java
convertAndSendToUser(userId.toString(), "/queue/presence", event)   // server
client.subscribe("/user/queue/presence", handler)                    // client
```

khớp nhau **không cần bảng tra cứu trung gian**. Đổi `getName()` sang email hay username sẽ làm hỏng
toàn bộ luồng gửi riêng — đây là lý do nó được ghi rõ trong Javadoc của record.

Không dùng `UsernamePasswordAuthenticationToken` như REST vì `spring-security-messaging` không nằm
trong dependency, nên không có bộ giải tham số `@AuthenticationPrincipal` cho `@MessageMapping`.

### 6.3. Vì sao dùng Spring Event thay vì gọi thẳng `SimpMessagingTemplate`

Ba lợi ích, phát hiện dần trong lúc làm:

1. **Không đường nào quên broadcast.** Tin gửi qua REST (Phase 3) và qua STOMP (Phase 4) đi chung
   một đường phát sóng.
2. **Service nghiệp vụ không phụ thuộc tầng vận chuyển.** `MessageServiceIntegrationTest` của Phase 3
   vẫn chạy nguyên vẹn mà không cần dựng WebSocket.
3. **Phase 5 cắm thêm được mà không sửa `MessageService`.** `NotificationEventListener` nghe đúng
   `MessageSentEvent` — và điều này đã thành sự thật ở Phase 5.

### 6.4. `TypingEvent` không lưu database

Dữ liệu chỉ có ý nghĩa trong đúng vài giây; ghi xuống đĩa là lãng phí I/O trên bảng nóng nhất hệ
thống, và mất frame cũng không gây hậu quả gì. Đây là ranh giới rõ ràng giữa "sự kiện thoáng qua"
(typing, presence) và "sự thật cần lưu" (tin nhắn, con trỏ đã đọc).

### 6.5. Lỗi nghiệp vụ không được đóng kết nối

Lỗi ở `CONNECT` thì đóng phiên (không có danh tính thì mọi frame sau đều vô nghĩa). Nhưng lỗi nghiệp
vụ ở `@MessageMapping` thì **không**: gõ nhầm vào một hội thoại vừa bị xóa mà mất luôn cả phiên
real-time là quá nặng tay. Thay vào đó trả lỗi riêng cho người gửi qua `/user/queue/errors`, dùng
lại đúng phong bì `ApiResponse` của REST để client chỉ phải hiểu một định dạng lỗi.

`@MessageExceptionHandler(Exception.class)` bắt phần còn lại — không có nó thì exception chỉ nằm
trong log server còn client chờ mãi một phản hồi không bao giờ tới, triệu chứng "bấm gửi không thấy
gì xảy ra" rất khó chẩn đoán.

---

## 7. BẢN ĐỒ MÃ LỖI

| Mã | Ở đâu | Hệ quả |
|---|---|---|
| `WEBSOCKET_UNAUTHORIZED` | CONNECT thiếu token / token hỏng | `ERROR` frame, **đóng phiên** |
| `WEBSOCKET_SUBSCRIPTION_DENIED` | SUBSCRIBE topic không phải thành viên, hoặc destination lạ | `ERROR` frame, **đóng phiên** |
| `NOT_CONVERSATION_MEMBER` | Gửi tin/typing/markRead vào hội thoại không thuộc về mình | `/user/queue/errors`, giữ phiên |
| `CONVERSATION_NOT_FOUND` | `conversation_id` không tồn tại | `/user/queue/errors`, giữ phiên |
| `MESSAGE_NOT_FOUND` | `message_id` ở markRead không tồn tại | `/user/queue/errors`, giữ phiên |
| `MESSAGE_NOT_IN_CONVERSATION` | markRead với message thuộc hội thoại khác | `/user/queue/errors`, giữ phiên |
| `USER_BLOCKED` | (Phase 2, dùng lại) gửi tin DIRECT khi bị chặn | `/user/queue/errors`, giữ phiên |
| `INTERNAL_ERROR` | Mọi lỗi ngoài dự kiến | `/user/queue/errors`, giữ phiên |

Hai mã đầu mang `HttpStatus` trong enum nhưng **không** dùng để set mã HTTP (STOMP không có khái
niệm này) — chỉ giữ để `ErrorCode` có duy nhất một hình dạng.

---

## 8. BẢO MẬT & RIÊNG TƯ

| Nguy cơ | Biện pháp | Ở đâu |
|---|---|---|
| Kết nối WebSocket ẩn danh | Xác thực JWT tại `CONNECT`, phiên không hợp lệ bị đóng ngay | `WebSocketAuthInterceptor.authenticate` |
| **Nghe trộm hội thoại người khác qua SUBSCRIBE** | Kiểm tra participant active theo từng destination | `WebSocketAuthInterceptor.authorizeSubscription` |
| Nghe ké queue riêng của người khác | `/user/**` được Spring dịch sang phiên của chính người subscribe | Cơ chế `UserDestinationMessageHandler` |
| Gửi tin/typing vào hội thoại lạ | Dùng lại `getActiveParticipantOrThrow()`; typing gọi `assertParticipant()` | `MessageService` |
| Lộ trạng thái online cho người lạ | Chỉ phát cho bạn bè, tôn trọng `online_visibility` (UC-13) | `PresenceBroadcaster.broadcast` |
| Phân biệt "offline" với "không cho xem" | `GET /presence/{userId}` trả `false` cho cả hai, không trả 403 | `PresenceController.isOnline` |
| Rò rỉ lý do từ chối qua thông báo lỗi | Client chỉ nhận mô tả chung của `ErrorCode`; chi tiết ("thiếu token" hay "sai chữ ký", hội thoại nào) chỉ nằm trong log server | `WebSocketAuthInterceptor.reject` |

---

## 9. TEST

**78 test, tất cả xanh** (8 test mới Phase 4).

### `ChatWebSocketIntegrationTest` — 8 test, WebSocket thật trên TCP thật

| Test | Xác nhận điều gì |
|---|---|
| `hai_client_cung_nhan_duoc_tin_nhan_realtime_khong_can_goi_lai_api` | Kịch bản nghiệm thu chính của roadmap |
| `tin_nhan_gui_qua_rest_cung_duoc_phat_realtime` | Đường REST của Phase 3 cũng broadcast (§5.3) |
| `typing_duoc_broadcast_toi_thanh_vien_khac` | UC-24 typing |
| `mark_read_cap_nhat_con_tro_va_broadcast_bien_nhan` | UC-24 read receipt + `unread_count` về 0 |
| `ban_be_nhan_duoc_su_kien_online_khi_co_nguoi_ket_noi` | Presence phát đúng người |
| `dong_mot_trong_hai_tab_van_giu_trang_thai_online` | Đếm phiên, không dùng cờ boolean (§5.5) |
| `connect_bi_tu_choi_khi_token_khong_hop_le` | Chốt xác thực |
| `khong_the_subscribe_topic_cua_hoi_thoai_minh_khong_tham_gia` | Chốt phân quyền (§5.2) |

### Ba quyết định trong lớp test đáng ghi lại

**1. Không kế thừa `AbstractIntegrationTest`.** Lớp cha dùng `webEnvironment = MOCK` (MockMvc, không
mở cổng thật). WebSocket cần một cổng TCP thật để bắt tay và nâng cấp giao thức — không giả lập được
bằng MockMvc. Cái giá: `RANDOM_PORT` tạo application context riêng kèm bộ container riêng, suite
chạy lâu hơn khoảng 20 giây.

**2. Dùng lại chính `brokerMessageConverter` của server ở phía client.**

```java
@Autowired @Qualifier("brokerMessageConverter")
private MessageConverter brokerMessageConverter;
```

Tự tạo một converter JSON mới ở phía test là cái bẫy kinh điển: mapper của test và của server có thể
khác nhau về quy ước đặt tên (`snake_case`) hay cách ghi `Instant`, và test sẽ đỏ vì **lệch cấu hình
JSON** chứ không phải vì code sai.

**3. Chờ 400ms sau khi subscribe.** SUBSCRIBE và SEND đi trên hai kết nối TCP khác nhau nên không có
thứ tự bảo đảm giữa chúng. Không chờ thì tin có thể tới broker lúc chưa ai đăng ký và bị bỏ đi hoàn
toàn **đúng theo thiết kế** (topic không lưu trữ), khiến test đỏ vì lý do sai. Đây là điểm chưa đẹp
nhưng trung thực — simple broker của Spring không gửi RECEIPT frame cho SUBSCRIBE nên không có mốc
đồng bộ tất định để chờ.

---

## 10. QUYẾT ĐỊNH LỆCH ROADMAP

Roadmap mục 4.3 viết:

> `@MessageMapping("/chat.sendMessage")`: ... gọi lại `MessageService.sendMessage()`, **sau đó
> `simpMessagingTemplate.convertAndSend(...)`**

Bản cài đặt **không** làm vậy. Controller trả `void` và không phát gì; việc phát sóng do
`ChatRealtimeBroadcaster` đảm nhiệm qua Spring Event + `AFTER_COMMIT`.

**Lý do:** cách của roadmap để lại một lỗ thật — tin nhắn gửi qua **REST** sẽ không được broadcast.
Người nhận đang mở sẵn cửa sổ chat vẫn phải F5, tức là đúng vấn đề mà Phase 4 sinh ra để giải quyết,
chỉ còn sót ở một nửa số đường vào. Ngoài ra `AFTER_COMMIT` loại bỏ khả năng phát frame cho một tin
nhắn rốt cuộc bị rollback (§5.3).

Cách này cũng **khớp hơn** với chính roadmap ở Phase sau: mục 5.2 dự kiến `NotificationEventListener`
nghe `MessageSentEvent` — sự kiện đó chỉ tồn tại nếu Phase 4 đã dựng nó. Thực tế Phase 5 cắm vào mà
không phải sửa một dòng nào của `MessageService`.

**Cái giá phải trả:** thêm 4 file (3 event + 1 broadcaster) và một tầng gián tiếp — muốn biết tin
nhắn được gửi đi đâu thì phải lần theo event chứ không đọc thẳng ở controller. Đánh đổi này được coi
là xứng đáng và đã ghi chú trong Javadoc của `MessageSentEvent`.

---

## 11. NỢ KỸ THUẬT

| Món nợ | Ảnh hưởng | Khi nào trả |
|---|---|---|
| **Simple broker không scale ngang** | 2 instance = tin nhắn không tới được nửa số người dùng | Trước khi lên production đa instance (`04_PRODUCTION_DEPLOYMENT.md`) |
| **Token chỉ kiểm tra tại CONNECT** | Phiên đang mở sống tiếp dù access token hết hạn | Chấp nhận được; muốn chặt hơn thì kiểm tra hạn ở mỗi frame SEND |
| **`Thread.sleep(400)` trong test** | Test chậm hơn cần thiết, về lý thuyết có thể flaky trên máy tải nặng | Khi tìm được mốc đồng bộ tất định thay thế |
| **`filterOnline()` gọi SCARD lần lượt** | N round-trip Redis cho danh sách N bạn bè | Phase 8 nếu đo thấy chậm — gộp bằng pipeline |
| **Presence không phát khi kết bạn mới** | Vừa kết bạn xong phải F5 mới thấy chấm xanh của bạn mới | Nhỏ; gọi lại `GET /presence/friends` sau khi accept là đủ |
| **Chưa theo dõi "đang mở hội thoại nào"** | Phase 5 tạo thông báo cho cả người đang xem đúng hội thoại đó | Cần theo dõi subscription đang hoạt động |
| **Không có ACK/retry tầng ứng dụng** | Frame mất khi mạng chập chờn thì mất luôn | Dữ liệu đã ở DB; client gọi lại `GET /messages` sau reconnect |

---

## 12. CHẠY THỬ BẰNG TAY

```bash
# 0. Bật hạ tầng (nếu chưa)
docker compose -f infra/docker-compose.yml up -d

# 1. Chạy app
./mvnw spring-boot:run

# 2. Đăng ký + xác thực + đăng nhập 2 user (xem 07_API_REFERENCE_AUTH.md)
#    -> $ALICE và $BOB là 2 access_token

# 3. Alice tạo hội thoại 1-1 với Bob
curl -X POST http://localhost:8080/api/v1/conversations/direct \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d '{"user_id":"<BOB_USER_ID>"}'

# 4. Xem ai đang online (trạng thái nền — WebSocket chỉ phát THAY ĐỔI)
curl -H "Authorization: Bearer $ALICE" http://localhost:8080/api/v1/presence/friends

# 5. Chạy riêng test nghiệm thu Phase 4 (dựng 2 client STOMP thật)
./mvnw test -Dtest=ChatWebSocketIntegrationTest
```

**Kiểm tra hoàn thành Phase 4** (cần frontend hoặc Postman WebSocket Request):

| Việc làm | Kết quả mong đợi |
|---|---|
| Alice gửi tin | Bob thấy ngay, không F5 |
| Alice gõ phím | Bob thấy "Đang soạn tin..." |
| Bob mở hội thoại | Alice thấy "Đã xem" |
| Alice đóng tab | Sau ~10s chấm xanh chuyển xám ở máy Bob |
| Alice F5 trong 10s | Chấm xanh **không** nhấp nháy |
| Alice mở 2 tab, đóng 1 | Vẫn xanh |

Chi tiết payload và mã client tham khảo: `13_API_REFERENCE_REALTIME.md`.

---

*Hết tài liệu 12_PHASE4_REALTIME_REPORT.md — Phase 5 (Media & Notification) xem
`14_PHASE5_MEDIA_NOTIFICATION_REPORT.md`.*
