# LỘ TRÌNH CODE ĐẦY ĐỦ
## ChatSphere — Từ dự án rỗng đến hệ thống hoàn chỉnh

**Tài liệu liên quan:** `01_SYSTEM_DESIGN.md` (thiết kế), `02_SETUP_GUIDE.md` (đã setup xong môi trường trước khi đọc file này)

Lộ trình chia thành **9 Phase**, mỗi Phase là 1 khối tính năng hoàn chỉnh có thể chạy/test độc lập (theo nguyên tắc phát triển tăng dần — incremental development). Thứ tự các Phase được sắp xếp để Phase sau luôn dùng lại được nền tảng của Phase trước, tránh phải quay lại sửa kiến trúc giữa chừng.

---

## TỔNG QUAN CÁC PHASE

| Phase | Tên | Nội dung chính | Ước lượng thời gian (học part-time) |
|---|---|---|---|
| 0 | Khởi tạo & hạ tầng chung | Base entity, exception handler, response wrapper, config chung | 0.5 ngày |
| 1 | Auth Module | Đăng ký, đăng nhập, JWT, refresh token, email xác thực | 2-3 ngày |
| 2 | User & Friend Module | Profile, avatar, tìm kiếm, kết bạn, chặn, cài đặt | 2 ngày |
| 3 | Chat Module (REST) | Conversation, Message CRUD qua REST trước (chưa real-time) | 2-3 ngày |
| 4 | Real-time Chat (WebSocket/STOMP) | Kết nối WS, gửi/nhận tin nhắn real-time, typing, presence | 3-4 ngày |
| 5 | Media & Notification | Upload file/ảnh, push notification, mute | 2 ngày |
| 6 | WebRTC Signaling (Backend) | Signaling module, CallSession, ICE server credential | 2-3 ngày |
| 7 | WebRTC Video Call (Frontend) | RTCPeerConnection, UI cuộc gọi, screen share | 3-4 ngày |
| 8 | Hoàn thiện, test, tối ưu | Unit test, integration test, sửa bug, tối ưu index | 2-3 ngày |

**Tổng ước lượng: khoảng 3-4 tuần** làm part-time (2-3 giờ/ngày) cho 1 người tự học.

---

## PHASE 0: KHỞI TẠO & HẠ TẦNG CHUNG

**Mục tiêu**: dựng bộ khung dùng chung cho toàn bộ dự án trước khi viết bất kỳ tính năng nghiệp vụ nào.

### Checklist

- [ ] 0.1. Tạo `BaseEntity` (abstract class) chứa `id`, `createdAt`, `updatedAt` dùng `@MappedSuperclass` + `@EntityListeners(AuditingEntityListener.class)`.
- [ ] 0.2. Bật `@EnableJpaAuditing` trong class cấu hình.
- [ ] 0.3. Tạo `ApiResponse<T>` wrapper class chuẩn hóa format response (theo mục 8.1 file `01_SYSTEM_DESIGN.md`).
- [ ] 0.4. Tạo `ErrorCode` enum liệt kê toàn bộ mã lỗi nghiệp vụ (ví dụ `USER_NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `INVALID_CREDENTIALS`...).
- [ ] 0.5. Tạo `BusinessException` (custom exception mang `ErrorCode`) và `GlobalExceptionHandler` (`@RestControllerAdvice`) bắt exception → trả `ApiResponse` lỗi thống nhất.
- [ ] 0.6. Cấu hình `CorsConfig` đọc từ `app.cors.allowed-origins`.
- [ ] 0.7. Cấu hình `JacksonConfig` (format ngày giờ ISO-8601, xử lý timezone UTC nhất quán).
- [ ] 0.8. Viết `application-test.yml` + cấu hình Testcontainers cho môi trường test (dùng Postgres container riêng, tách biệt DB dev).
- [ ] 0.9. Tạo package structure đầy đủ theo mục 3.3 file `01_SYSTEM_DESIGN.md`.
- [ ] 0.10. Viết test đầu tiên: `HealthCheckTest` gọi `/actuator/health` để xác nhận bộ khung chạy được qua Testcontainers.

**Kết quả đầu ra**: Project chạy được, có cấu trúc exception/response chuẩn, sẵn sàng thêm module nghiệp vụ.

---

## PHASE 1: AUTH MODULE

**Use case liên quan**: UC-01 → UC-07

### 1.1. Entity & Migration

- [ ] Viết migration `V1__create_users_table.sql`, `V10__create_refresh_tokens...sql` (có thể đổi thứ tự số hợp lý hơn nếu cần).
- [ ] Tạo entity `User` (kế thừa `BaseEntity`), enum `UserStatus`, `UserRole`.
- [ ] Tạo entity `RefreshToken`.

### 1.2. Security Configuration

- [ ] Tạo `JwtTokenProvider`: sinh access token, refresh token, validate token, extract claims (userId, role).
  > **Lưu ý kỹ thuật quan trọng**: JWT dùng cho REST API xác thực qua `Authorization` header bình thường, nhưng JWT dùng cho WebSocket phải xác thực tại thời điểm STOMP `CONNECT` frame (xem Phase 4) — cần thiết kế `JwtTokenProvider` dùng chung được cho cả 2 luồng.
- [ ] Tạo `CustomUserDetailsService` implement `UserDetailsService`.
- [ ] Tạo `JwtAuthenticationFilter` (`OncePerRequestFilter`) — parse header, validate, set `SecurityContext`.
- [ ] Tạo `SecurityConfig`: cấu hình `SecurityFilterChain`, whitelist endpoint public (`/api/v1/auth/**`, `/swagger-ui/**`), stateless session, method security (`@EnableMethodSecurity`).
- [ ] Tạo `PasswordEncoderConfig` (BCryptPasswordEncoder, strength=12).

### 1.3. DTO

- [ ] `RegisterRequest` (email, password, username, displayName) — validate `@Email`, `@Size(min=8)`, `@Pattern` (mật khẩu có chữ hoa/số).
- [ ] `LoginRequest`, `LoginResponse` (accessToken, refreshToken, expiresIn).
- [ ] `RefreshTokenRequest`.
- [ ] `ForgotPasswordRequest`, `ResetPasswordRequest`, `ChangePasswordRequest`.
- [ ] `VerifyEmailRequest`.

### 1.4. Service Layer

- [ ] `AuthService.register()`: validate email/username unique, mã hóa password, tạo user `PENDING_VERIFICATION`, sinh OTP/token xác thực, gọi `EmailService` gửi mail.
- [ ] `AuthService.verifyEmail()`: kiểm tra token, cập nhật `status=ACTIVE`.
- [ ] `AuthService.login()`: xác thực qua `AuthenticationManager`, kiểm tra `status=ACTIVE`, sinh cặp token, lưu `RefreshToken` (hash bằng SHA-256 trước khi lưu DB — không lưu raw token), cập nhật `lastLoginAt`.
  > **Chống brute-force**: dùng Redis counter theo key `login_attempt:{email}`, TTL 15 phút, khóa khi >= 5 lần sai.
- [ ] `AuthService.refresh()`: validate refresh token còn hạn + chưa revoke, sinh access token mới (+ rotation: thu hồi token cũ, cấp token mới).
- [ ] `AuthService.logout()`: đánh dấu `revoked=true` cho refresh token.
- [ ] `AuthService.forgotPassword()` / `resetPassword()`: sinh token reset riêng (khác JWT, dùng UUID lưu Redis TTL 15 phút).
- [ ] `AuthService.changePassword()`: verify mật khẩu cũ, cập nhật mới, thu hồi toàn bộ refresh token cũ của user.

### 1.5. Controller

- [ ] `AuthController` implement toàn bộ endpoint mục 8.2 nhóm `/auth/*`.

### 1.6. Email Service

- [ ] `EmailService` dùng `JavaMailSender`, template email bằng Thymeleaf (hoặc plain text đơn giản cho bản học tập).

### 1.7. Test

- [ ] Unit test `AuthServiceTest` (Mockito) cho từng luồng chính + luồng ngoại lệ (email trùng, sai mật khẩu, token hết hạn).
- [ ] Integration test `AuthControllerIT` (Testcontainers + MockMvc) test đầy đủ luồng đăng ký → xác thực → đăng nhập → refresh → logout.

**Kiểm tra hoàn thành Phase 1**: dùng Swagger UI hoặc Postman thực hiện được trọn vẹn: đăng ký → nhận mã OTP qua MailHog → xác thực → đăng nhập → nhận JWT → gọi 1 API bảo vệ bằng token đó thành công.

---

## PHASE 2: USER & FRIEND MODULE

**Use case liên quan**: UC-08 → UC-13

### 2.1. Entity & Migration

- [ ] `UserSettings`, `FriendRequest`, `Friendship`, `BlockedUser`.

### 2.2. DTO & Mapper

- [ ] `UserProfileResponse`, `UpdateProfileRequest`, `UserSearchResponse` (thông tin rút gọn, không lộ email nếu không phải chính chủ).
- [ ] `UserMapper` (MapStruct).

### 2.3. Service Layer

- [ ] `UserService.getMyProfile()`, `updateProfile()`, `uploadAvatar()` (gọi `MediaService` ở Phase 5 — có thể tạm dùng service upload đơn giản trước, refactor sau).
- [ ] `UserService.search(keyword, pageable)`: query full-text hoặc `LIKE` (nâng cấp full-text ở Phase 8).
- [ ] `FriendService.sendRequest()`: kiểm tra chưa là bạn, chưa có request PENDING, chưa bị block.
- [ ] `FriendService.acceptRequest()`: tạo `Friendship` (đảm bảo `userId1 < userId2`), xóa/cập nhật request.
- [ ] `FriendService.rejectRequest()`, `cancelRequest()`.
- [ ] `FriendService.getFriendList()`, `removeFriend()`.
- [ ] `BlockService.blockUser()`, `unblockUser()`, `isBlocked(userId1, userId2)` (dùng ở nhiều nơi: chat, call — nên cache kết quả này trong Redis với TTL ngắn nếu cần tối ưu).
- [ ] `UserSettingsService.updateSettings()`.

### 2.4. Controller

- [ ] `UserController`, `FriendController` theo mục 8.2.

### 2.5. Test

- [ ] Unit test cho toàn bộ luồng ngoại lệ: gửi lời mời cho người đã chặn mình, chấp nhận request không tồn tại, kết bạn với chính mình.

**Kiểm tra hoàn thành Phase 2**: tạo 2 user, thực hiện gửi/nhận/chấp nhận lời mời kết bạn, xem danh sách bạn bè, thử chặn 1 user và xác nhận API trả lỗi khi user bị chặn cố gắng tương tác.

---

## PHASE 3: CHAT MODULE (REST TRƯỚC, CHƯA REAL-TIME)

**Use case liên quan**: UC-14 → UC-17, một phần UC-18/19 (chỉ CRUD qua REST)

> **Lý do tách Phase này riêng khỏi WebSocket (Phase 4)**: xây dựng và test logic nghiệp vụ (ai được phép nhắn ai, cấu trúc conversation/message) bằng REST API + Postman sẽ dễ debug hơn nhiều so với vừa viết business logic vừa vật lộn với WebSocket. Sau khi chắc chắn logic đúng, Phase 4 chỉ việc "real-time hóa" một lớp mỏng bên trên.

### 3.1. Entity & Migration

- [ ] `Conversation`, `ConversationParticipant`, `Message` (chưa cần `MessageAttachment`, `MessageReaction` — thêm ở Phase 5).

### 3.2. DTO & Mapper

- [ ] `ConversationResponse` (kèm `lastMessage`, `unreadCount`, danh sách participant rút gọn).
- [ ] `CreateGroupRequest`, `UpdateGroupRequest`, `MessageResponse`, `SendMessageRequest`.

### 3.3. Service Layer

- [ ] `ConversationService.getOrCreateDirectConversation(userId1, userId2)`: kiểm tra đã tồn tại chưa trước khi tạo mới (tránh trùng lặp).
- [ ] `ConversationService.createGroup()`: tạo conversation + participant cho từng thành viên, người tạo là `ADMIN`.
- [ ] `ConversationService.getMyConversations(pageable)`: sắp xếp theo `updatedAt DESC`, tính `unreadCount` cho từng conversation.
- [ ] `ConversationService.addMember()`, `removeMember()`, `updateGroupInfo()` — kiểm tra quyền `ADMIN`.
- [ ] `ConversationService.leaveGroup()`: xử lý case admin cuối cùng rời nhóm (tự động chuyển quyền — theo UC-17).
- [ ] `MessageService.sendMessage()`: validate người gửi là participant hợp lệ + chưa bị block bởi bất kỳ ai trong conversation (với DIRECT) → lưu message → cập nhật `conversation.lastMessageId/updatedAt`.
- [ ] `MessageService.getMessages(conversationId, cursor, limit)`: cursor-based pagination theo `createdAt`.
- [ ] `MessageService.recallMessage()`: kiểm tra quyền sở hữu + thời gian (< 5 phút).

### 3.4. Controller

- [ ] `ConversationController`, `MessageController` theo mục 8.2.

### 3.5. Test

- [ ] Test đầy đủ luồng: tạo group 3 người → gửi tin nhắn → lấy lịch sử phân trang → thu hồi tin nhắn → rời nhóm.

**Kiểm tra hoàn thành Phase 3**: toàn bộ luồng chat hoạt động đúng qua REST (chưa real-time — người nhận phải tự gọi lại API để thấy tin nhắn mới, đây là hành vi mong đợi ở Phase này).

---

## PHASE 4: REAL-TIME CHAT (WEBSOCKET/STOMP)

**Use case liên quan**: UC-18 (real-time), UC-24 (typing, read status), presence

### 4.1. WebSocket Configuration

- [ ] `WebSocketConfig` implement `WebSocketMessageBrokerConfigurer`:
  - `registerStompEndpoints()`: đăng ký `/ws` với SockJS fallback.
  - `configureMessageBroker()`: bật simple broker cho `/topic`, `/queue`, prefix ứng dụng `/app`, prefix user destination `/user`.
- [ ] `WebSocketAuthInterceptor` (implement `ChannelInterceptor`): bắt `StompCommand.CONNECT`, extract JWT từ header, validate, set `Principal`.
  > **Điểm học thuật quan trọng**: STOMP over WebSocket không tự động gửi HTTP header như REST — JWT phải được client gửi trong native STOMP header của frame `CONNECT`, xử lý ở tầng `ChannelInterceptor` chứ không phải filter HTTP thông thường.
- [ ] Đăng ký interceptor vào `configureClientInboundChannel()`.

### 4.2. Presence Module (Redis)

- [ ] `PresenceService`: lưu `userId -> Set<sessionId>` trong Redis (1 user có thể mở nhiều tab/thiết bị).
- [ ] `WebSocketEventListener` lắng nghe `SessionConnectedEvent` (thêm mapping, publish online) và `SessionDisconnectEvent` (xóa mapping, nếu hết session thì publish offline — debounce ~10s theo thiết kế mục 9.1 file 01).
- [ ] Publish trạng thái online/offline đến `/user/{friendId}/queue/presence` cho từng bạn bè (chỉ gửi cho bạn bè, tôn trọng cài đặt `online_visibility` từ UC-13).

### 4.3. Chat Real-time Controller

- [ ] `ChatWebSocketController`:
  - `@MessageMapping("/chat.sendMessage")`: nhận `SendMessageRequest`, gọi lại `MessageService.sendMessage()` (tái sử dụng Phase 3), sau đó `simpMessagingTemplate.convertAndSend("/topic/conversation/{id}", messageResponse)`.
  - `@MessageMapping("/chat.typing")`: broadcast `TypingEvent` đến các thành viên khác (không lưu DB).
  - `@MessageMapping("/chat.markRead")`: cập nhật `lastReadMessageId`, broadcast `READ` receipt.

### 4.4. Frontend — Kết nối WebSocket

- [ ] `websocketClient.ts`: khởi tạo STOMP client với SockJS, gắn JWT vào `connectHeaders`.
- [ ] Hook `useConversationSocket(conversationId)`: subscribe `/topic/conversation/{id}`, cleanup khi unmount.
- [ ] Hook `usePresenceSocket()`: subscribe `/user/queue/presence`.
- [ ] UI: hiển thị tin nhắn realtime, chấm xanh online, "Đang soạn tin...".

### 4.5. Test

- [ ] Test WebSocket bằng `WebSocketStompClient` trong integration test (giả lập 2 client kết nối, gửi tin nhắn, xác nhận cả 2 đều nhận được qua topic).

**Kiểm tra hoàn thành Phase 4**: mở 2 trình duyệt (hoặc 2 tab ẩn danh) đăng nhập 2 tài khoản khác nhau, nhắn tin qua lại thấy hiển thị ngay lập tức không cần F5, thấy trạng thái online và "đang soạn tin" hoạt động đúng.

---

## PHASE 5: MEDIA & NOTIFICATION MODULE

**Use case liên quan**: UC-19, UC-22, UC-23, UC-26 → UC-28

### 5.1. Media Module

- [ ] `MinioConfig`: khởi tạo `MinioClient` bean, tự tạo bucket nếu chưa tồn tại khi ứng dụng start.
- [ ] `MediaService.uploadFile()`: validate MIME type qua magic byte (dùng thư viện `Apache Tika`), giới hạn kích thước theo loại file, generate tên file unique (UUID + extension), upload lên MinIO, trả về URL.
- [ ] `MediaController`: endpoint `/api/v1/media/upload`.
- [ ] Cập nhật `MessageService` hỗ trợ `type=IMAGE/FILE/VOICE` kèm `MessageAttachment`.
- [ ] Entity `MessageAttachment`, `MessageReaction`, `MessageDeletion` + migration tương ứng.
- [ ] `MessageService.addReaction()`, `forwardMessage()`, `deleteForMe()`.

### 5.2. Notification Module

- [ ] Entity `Notification` + migration.
- [ ] `NotificationService.create()`: tạo record + gửi qua `/user/{userId}/queue/notifications` nếu đang online.
- [ ] Dùng Spring Event: `MessageSentEvent` → `NotificationEventListener` xử lý bất đồng bộ (`@Async` + `@EventListener`) tạo notification cho thành viên offline/không mở đúng conversation — tách rời khỏi luồng gửi tin nhắn chính (không làm chậm response gửi tin).
- [ ] `NotificationController`: lấy danh sách, đánh dấu đã đọc.

### 5.3. Web Push Notification

- [ ] Sinh cặp khóa VAPID (`web-push` library có tool generate).
- [ ] Entity `PushSubscription` + migration.
- [ ] `PushNotificationService.send()`: dùng thư viện `web-push` gửi đến endpoint trình duyệt.
- [ ] Frontend: đăng ký Service Worker (`sw.js`), xin quyền notification, subscribe push, gửi subscription lên server.

### 5.4. Mute Conversation

- [ ] `ConversationService.muteConversation(conversationId, until)`: cập nhật `mutedUntil` — `NotificationEventListener` kiểm tra field này trước khi gửi.

### 5.5. Test

- [ ] Test upload file với file giả mạo extension (đổi đuôi `.exe` thành `.jpg`) → xác nhận bị chặn bởi kiểm tra magic byte.
- [ ] Test notification không được tạo khi conversation đang bị mute.

**Kiểm tra hoàn thành Phase 5**: gửi được ảnh/file trong chat, thả reaction thấy cập nhật realtime, nhận được thông báo trình duyệt khi đóng tab (Web Push).

---

## PHASE 6: WEBRTC SIGNALING (BACKEND)

**Use case liên quan**: UC-29 → UC-35 (phần backend)

### 6.1. Entity & Migration

- [ ] `CallSession` + migration.

### 6.2. ICE Server Credential Endpoint

- [ ] `IceServerService.generateTurnCredential()`: implement thuật toán HMAC-SHA1 time-limited credential (đã minh họa ở mục 8.1 file `02_SETUP_GUIDE.md`), đọc `TURN_SECRET` từ config.
- [ ] `CallController.getIceServers()`: trả về danh sách ICE server (STUN cố định + TURN với credential vừa sinh, TTL ví dụ 1 giờ).

### 6.3. Signaling WebSocket Controller

- [ ] `CallSignalingController`:
  - `@MessageMapping("/call.invite")`: validate callee tồn tại, không bị block, cho phép nhận cuộc gọi (`call_permission` setting), callee đang online → tạo `CallSession(status=RINGING)` → forward đến `/user/{calleeId}/queue/call` với type `CALL_INVITE`.
  - `@MessageMapping("/call.accept")`: cập nhật liên quan, forward `CALL_ACCEPT` về caller.
  - `@MessageMapping("/call.decline")`: cập nhật `status=DECLINED`, forward về caller.
  - `@MessageMapping("/call.signal")`: forward nguyên payload SDP Offer/Answer/ICE Candidate từ người gửi đến người nhận tương ứng (**server không parse/hiểu nội dung SDP, chỉ đóng vai trò relay tín hiệu** — đúng nguyên tắc thiết kế mục 9.2 file 01).
  - `@MessageMapping("/call.end")`: cập nhật `status=ENDED`, tính `durationSeconds`, forward `CALL_END`.
- [ ] `CallTimeoutScheduler` (`@Scheduled`): định kỳ quét các `CallSession` ở trạng thái `RINGING` quá 30 giây → tự động chuyển `MISSED`, gửi `CALL_TIMEOUT` cho cả 2 phía.

### 6.4. Test

- [ ] Test giả lập 2 STOMP client trao đổi đầy đủ chuỗi tín hiệu invite → accept → offer → answer → ice candidate → end, xác nhận `CallSession` được cập nhật đúng trạng thái qua từng bước.
- [ ] Test callee không phản hồi sau 30s → xác nhận `CallTimeoutScheduler` cập nhật đúng `MISSED`.

**Kiểm tra hoàn thành Phase 6**: dùng 2 STOMP client giả lập (script test hoặc Postman WebSocket) xác nhận toàn bộ chuỗi tín hiệu được relay đúng người, đúng thứ tự, `CallSession` phản ánh đúng trạng thái thực tế.

---

## PHASE 7: WEBRTC VIDEO CALL (FRONTEND)

**Use case liên quan**: UC-29 → UC-35 (phần frontend — đây là phần khó và thú vị nhất của dự án)

### 7.1. Chuẩn bị Media

- [ ] `useLocalMedia()` hook: gọi `navigator.mediaDevices.getUserMedia({audio, video})`, xử lý lỗi khi user từ chối quyền camera/mic.
- [ ] Hiển thị preview local stream trong `<video muted autoPlay playsInline>`.

### 7.2. Thiết lập RTCPeerConnection

- [ ] `useCallConnection()` hook — trung tâm của module video call:
  - Khởi tạo `new RTCPeerConnection({iceServers})` với ICE server lấy từ `/api/v1/calls/ice-servers` (Phase 6).
  - `pc.addTrack()` cho từng track của local stream.
  - Lắng nghe `pc.ontrack` → gắn remote stream vào `<video>` thứ 2.
  - Lắng nghe `pc.onicecandidate` → gửi candidate qua WebSocket (`/app/call.signal`).
  - Lắng nghe `pc.onconnectionstatechange` → xử lý khi `disconnected`/`failed` (thử reconnect hoặc thông báo lỗi).

### 7.3. Luồng Caller

- [ ] Gửi `CALL_INVITE` qua WebSocket.
- [ ] Khi nhận `CALL_ACCEPT` → tạo Offer (`pc.createOffer()`), `setLocalDescription()`, gửi qua `/app/call.signal` (type `SDP_OFFER`).
- [ ] Khi nhận `SDP_ANSWER` → `pc.setRemoteDescription()`.
- [ ] Khi nhận `ICE_CANDIDATE` từ callee → `pc.addIceCandidate()`.

### 7.4. Luồng Callee

- [ ] Nhận `CALL_INVITE` qua `/user/queue/call` → hiển thị popup accept/decline toàn màn hình + âm thanh chuông.
- [ ] Khi accept → gửi `CALL_ACCEPT`, chờ nhận `SDP_OFFER`.
- [ ] Nhận `SDP_OFFER` → tạo `RTCPeerConnection`, `setRemoteDescription()`, tạo Answer (`pc.createAnswer()`), `setLocalDescription()`, gửi `SDP_ANSWER`.
- [ ] Nhận `ICE_CANDIDATE` từ caller → `pc.addIceCandidate()`.

  > **Vấn đề kỹ thuật cần xử lý: ICE Candidate đến trước khi có Remote Description.** Vì tín hiệu là bất đồng bộ qua mạng, candidate có thể đến trước khi `setRemoteDescription` hoàn tất. Giải pháp: dùng 1 hàng đợi (queue) tạm lưu candidate đến sớm, chỉ `addIceCandidate()` sau khi remote description đã được set — đây là lỗi phổ biến nhất khi tự viết WebRTC signaling lần đầu.

### 7.5. Điều khiển cuộc gọi

- [ ] Toggle camera/mic: `track.enabled = !track.enabled` trên từng track tương ứng, gửi `MEDIA_STATE_CHANGED` để hiển thị icon cho đối phương.
- [ ] Chia sẻ màn hình: `getDisplayMedia()` → tìm `RTCRtpSender` của video track → `sender.replaceTrack(screenTrack)`; khi dừng → `replaceTrack(cameraTrack)`.
- [ ] Kết thúc cuộc gọi: `pc.close()`, dừng toàn bộ track (`track.stop()`), gửi `/app/call.end`.

### 7.6. Chất lượng kết nối

- [ ] `setInterval` mỗi 3s gọi `pc.getStats()`, tính packet loss %, hiển thị icon (xanh/vàng/đỏ).

### 7.7. Lịch sử cuộc gọi

- [ ] `CallHistoryPage`: gọi `/api/v1/calls/history`, hiển thị danh sách kèm icon loại cuộc gọi (gọi đi/đến/nhỡ), thời lượng.

### 7.8. Test thủ công bắt buộc (không thể unit test hoàn toàn WebRTC)

- [ ] Test 2 tab cùng máy (baseline — luôn phải thành công).
- [ ] Test 2 máy cùng mạng LAN.
- [ ] Test 2 máy khác mạng (1 wifi, 1 4G hotspot) — **đây là bài test thật sự chứng minh TURN server hoạt động đúng**; theo dõi qua `pc.getStats()` xem `candidate-pair` đang dùng loại `relay` (qua TURN) hay `srflx`/`host` (P2P trực tiếp).
- [ ] Test tắt/bật camera, chia sẻ màn hình giữa chừng cuộc gọi.
- [ ] Test từ chối cuộc gọi, không nghe máy (timeout 30s).

**Kiểm tra hoàn thành Phase 7**: thực hiện được cuộc gọi video hoàn chỉnh giữa 2 thiết bị khác mạng, xác nhận qua log/console rằng khi cần thiết, kết nối đã tự động relay qua TURN server.

---

## PHASE 8: HOÀN THIỆN, TEST, TỐI ƯU

### 8.1. Full-text Search

- [ ] Thêm cột `tsvector` (generated column) trên `messages.content`, GIN index — implement UC-25 đúng chuẩn thay vì `LIKE '%...%'`.
- [ ] Tương tự cho tìm kiếm user (UC-10) trên `display_name`/`username`.

### 8.2. Test Coverage

- [ ] Rà soát coverage bằng JaCoCo, đảm bảo Service layer >= 60% theo yêu cầu phi chức năng (mục 11 file 01).
- [ ] Viết thêm test cho các edge case còn thiếu: xóa user có đang trong cuộc gọi, gửi tin nhắn vào conversation đã bị xóa, race condition khi 2 người cùng chấp nhận 1 friend request.

### 8.3. Tối ưu hiệu năng

- [ ] `EXPLAIN ANALYZE` các query chậm nhất (đặc biệt query lấy danh sách conversation kèm unread count) — thêm index còn thiếu.
- [ ] Thêm cache Redis cho `IsBlocked` check, `UserSettings` (dữ liệu ít thay đổi, đọc nhiều).
- [ ] Kiểm tra N+1 query (bật `hibernate.generate_statistics` tạm thời để phát hiện).

### 8.4. UI/UX Polish

- [ ] Responsive kiểm tra trên mobile browser.
- [ ] Loading skeleton, empty state, error state cho toàn bộ màn hình chính.
- [ ] Dark mode (tùy chọn, TailwindCSS hỗ trợ sẵn `dark:` variant).

### 8.5. Tài liệu hóa code

- [ ] Đảm bảo mọi class/method phức tạp có Javadoc, đặc biệt module `signaling` và `presence`.
- [ ] Cập nhật Swagger annotation (`@Operation`, `@ApiResponse`) đầy đủ cho toàn bộ endpoint.

### 8.6. Chuẩn bị Production

- [ ] Chuyển sang đọc `04_PRODUCTION_DEPLOYMENT.md` để triển khai thật.

---

## GHI CHÚ VỀ THỨ TỰ ƯU TIÊN

Nếu thời gian hạn chế, thứ tự ưu tiên tối thiểu để có 1 sản phẩm demo được (MVP) là: **Phase 0 → 1 → 2 → 3 → 4 → 6 → 7**, bỏ qua tạm Phase 5 (Media/Notification) và quay lại sau — vì mục tiêu học tập cốt lõi (chat real-time + WebRTC signaling) không phụ thuộc vào module Media/Notification.

## GHI CHÚ VỀ QUẢN LÝ GIT

Khuyến nghị tạo 1 branch riêng cho mỗi Phase (`feature/phase-1-auth`, `feature/phase-2-user`...), merge vào `main` sau khi hoàn thành checklist + test của Phase đó — giúp dễ quay lại tham khảo tiến trình phát triển từng giai đoạn, đúng tinh thần "học từng bước".

---

*Hết tài liệu 03_CODE_ROADMAP.md — sau khi hoàn thành toàn bộ 9 Phase, xem `04_PRODUCTION_DEPLOYMENT.md` để đưa hệ thống lên môi trường thật.*
