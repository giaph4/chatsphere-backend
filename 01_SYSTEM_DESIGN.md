# TÀI LIỆU THIẾT KẾ HỆ THỐNG
## ChatSphere — Ứng dụng nhắn tin & gọi video thời gian thực

**Phiên bản:** 1.0
**Ngày:** 03/09/2026
**Loại tài liệu:** System Design Document (SDD)
**Đối tượng đọc:** Developer tự học, dùng làm dự án cá nhân để luyện kỹ năng Spring Boot / WebSocket / WebRTC

---

## MỤC LỤC

1. [Giới thiệu hệ thống](#1-giới-thiệu-hệ-thống)
2. [Công nghệ sử dụng](#2-công-nghệ-sử-dụng)
3. [Kiến trúc tổng thể](#3-kiến-trúc-tổng-thể)
4. [Danh sách tính năng đầy đủ](#4-danh-sách-tính-năng-đầy-đủ)
5. [Use Case tổng quát](#5-use-case-tổng-quát)
6. [Use Case chi tiết](#6-use-case-chi-tiết)
7. [Thiết kế Database](#7-thiết-kế-database)
8. [Thiết kế API](#8-thiết-kế-api)
9. [Thiết kế luồng WebSocket & Signaling](#9-thiết-kế-luồng-websocket--signaling)
10. [Bảo mật](#10-bảo-mật)
11. [Phi chức năng (Non-functional Requirements)](#11-phi-chức-năng-non-functional-requirements)

---

## 1. GIỚI THIỆU HỆ THỐNG

### 1.1. Tổng quan

**ChatSphere** là một ứng dụng web nhắn tin thời gian thực (real-time messaging) tích hợp gọi thoại/gọi video, được xây dựng nhằm mục đích học tập và luyện tập các công nghệ backend/real-time hiện đại. Hệ thống mô phỏng các tính năng cốt lõi của những ứng dụng nhắn tin phổ biến (Messenger, Zalo, Telegram) nhưng được tự thiết kế và triển khai từ đầu để người học hiểu rõ bản chất kỹ thuật bên dưới, đặc biệt là:

- Cơ chế giao tiếp hai chiều thời gian thực (WebSocket/STOMP).
- Cơ chế thiết lập kết nối media ngang hàng (WebRTC), bao gồm NAT traversal (STUN/TURN).
- Thiết kế hệ thống backend có trạng thái (stateful) kết hợp với hệ thống không trạng thái (stateless REST API).

### 1.2. Mục tiêu dự án

- Xây dựng được một hệ thống nhắn tin hoàn chỉnh: đăng ký/đăng nhập, chat 1-1, chat nhóm, gửi file/ảnh, trạng thái online, thông báo.
- Xây dựng được tính năng gọi video/gọi thoại 1-1 sử dụng WebRTC với signaling server tự viết bằng Spring Boot.
- Hiểu và áp dụng đúng các pattern kiến trúc: layered architecture, DTO pattern, Repository pattern, Pub/Sub.
- Có khả năng vận hành thử nghiệm hệ thống trên môi trường thật (production) ở quy mô nhỏ.

### 1.3. Phạm vi (Scope)

**Trong phạm vi (In-scope):**
- Web application (responsive, chạy tốt trên trình duyệt desktop và mobile browser).
- Chat 1-1 và chat nhóm (group chat).
- Gọi video/thoại 1-1 (one-to-one call).
- Quản lý tài khoản, bạn bè/liên hệ.
- Thông báo real-time.

**Ngoài phạm vi (Out-of-scope, có thể mở rộng sau):**
- Ứng dụng di động native (iOS/Android).
- Gọi video nhóm (group video call) quy mô lớn — vì cần kiến trúc SFU/MCU phức tạp hơn nhiều so với mesh P2P, được đề cập như hướng mở rộng ở mục 4.5.
- Mã hóa đầu cuối (end-to-end encryption) hoàn chỉnh cho tin nhắn — có đề cập hướng làm nhưng không bắt buộc trong bản v1.
- Đa ngôn ngữ (i18n) — có thể bổ sung sau.

### 1.4. Đối tượng người dùng

| Vai trò | Mô tả |
|---|---|
| **Guest** | Người dùng chưa đăng nhập, chỉ thấy trang giới thiệu/đăng nhập/đăng ký |
| **User** | Người dùng đã xác thực, sử dụng đầy đủ tính năng chat, gọi video, quản lý bạn bè |
| **Group Admin** | User có quyền quản trị trong 1 group chat cụ thể (thêm/xóa thành viên, đổi tên nhóm...) |
| **System Admin** | Quản trị hệ thống (xem thống kê, khóa tài khoản vi phạm) — tính năng mở rộng |

---

## 2. CÔNG NGHỆ SỬ DỤNG

### 2.1. Backend

| Thành phần | Công nghệ | Lý do lựa chọn |
|---|---|---|
| Ngôn ngữ | Java 21 (LTS) | Ổn định, hỗ trợ virtual threads (Project Loom) hữu ích cho I/O-bound app như chat |
| Framework | Spring Boot 3.3.x | Hệ sinh thái đầy đủ, tích hợp sẵn Security, WebSocket, Data JPA |
| Bảo mật | Spring Security 6 + JWT (jjwt) | Chuẩn công nghiệp, stateless authentication phù hợp REST API |
| Real-time | Spring WebSocket + STOMP + SockJS fallback | Có sẵn cơ chế pub/sub theo topic, dễ mở rộng, fallback cho trình duyệt cũ |
| ORM | Spring Data JPA + Hibernate | Giảm boilerplate, hỗ trợ tốt PostgreSQL |
| Database chính | PostgreSQL 16 | Quan hệ dữ liệu rõ ràng (user-conversation-message), hỗ trợ JSONB, full-text search |
| Cache / Session / Pub-Sub | Redis 7 | Lưu presence (online/offline), mapping session, cache, có thể làm message broker khi scale nhiều instance |
| Validation | Jakarta Bean Validation (Hibernate Validator) | Validate DTO chuẩn hóa |
| Build tool | Maven | Phổ biến, tích hợp tốt với Spring Initializr |
| API Docs | springdoc-openapi (Swagger UI) | Tự sinh tài liệu API để test nhanh |
| Migration DB | Flyway | Quản lý version schema database rõ ràng, cần thiết khi lên production |
| Object storage (file/ảnh) | MinIO (self-host, S3-compatible) hoặc AWS S3 | Lưu file đính kèm, ảnh đại diện, không lưu file trong DB |
| Mapping DTO | MapStruct | Generate code mapping Entity <-> DTO tại compile-time, tránh lỗi runtime |
| Test | JUnit 5, Mockito, Testcontainers | Unit test + integration test với DB thật trong container |

### 2.2. Video Call / WebRTC

| Thành phần | Công nghệ | Lý do lựa chọn |
|---|---|---|
| Signaling protocol | Custom JSON message qua WebSocket (STOMP) | Tự viết để hiểu rõ luồng offer/answer/ICE candidate |
| STUN server | Google public STUN (`stun:stun.l.google.com:19302`) khi dev; tự host `coturn` khi production | Miễn phí cho môi trường học tập |
| TURN server | `coturn` (Docker) | Bắt buộc để đảm bảo kết nối thành công khi cả 2 phía sau NAT đối xứng/firewall chặt |
| Media API (client) | WebRTC native API (`RTCPeerConnection`, `getUserMedia`) | Không dùng SDK bên thứ 3 — đúng mục tiêu học tập |

### 2.3. Frontend

| Thành phần | Công nghệ | Lý do lựa chọn |
|---|---|---|
| Framework | React 18 + TypeScript | Phổ biến, type-safe, dễ tìm tài liệu |
| Build tool | Vite | Khởi động nhanh, HMR tốt |
| State management | Zustand (nhẹ) hoặc Redux Toolkit | Quản lý state chat/call phức tạp (nhiều conversation, nhiều participant) |
| UI Library | TailwindCSS + shadcn/ui | Style nhanh, đẹp, dễ tùy biến |
| WebSocket client | `@stomp/stompjs` + `sockjs-client` | Tương thích trực tiếp với Spring STOMP broker |
| HTTP client | Axios | Interceptor xử lý JWT refresh dễ dàng |
| Form | React Hook Form + Zod | Validate form phía client |
| Routing | React Router v6 | Chuẩn cho SPA |

### 2.3.1. Lựa chọn thay thế đã cân nhắc

| Quyết định | Ưu điểm | Nhược điểm | Vì sao chọn |
|---|---|---|---|
| STOMP thay vì raw WebSocket | Có sẵn pub/sub theo topic/queue, tích hợp Spring Security dễ | Overhead thêm 1 lớp giao thức | Học được pattern dùng phổ biến trong hệ thống thực tế |
| PostgreSQL thay vì MongoDB | Quan hệ dữ liệu (user-conversation-message) rất rõ ràng, cần transaction | Không linh hoạt schema bằng NoSQL | Dữ liệu chat có cấu trúc quan hệ chặt, JOIN nhiều |
| Tự viết signaling thay vì dùng LiveKit/Agora | Hiểu sâu WebRTC, NAT traversal | Tốn thời gian, khó debug hơn | Đúng mục tiêu học tập đã chọn |
| JWT thay vì Session-based | Stateless, dễ scale ngang | Khó revoke token tức thời (cần thêm blacklist) | REST API cần stateless; WebSocket vẫn cần Redis lưu presence riêng |
| React thay vì Vue/Angular | Cộng đồng lớn, dễ tìm ví dụ WebRTC + React | Boilerplate nhiều hơn Vue | Phổ biến hơn khi cần tìm tài liệu tham khảo |

### 2.4. Hạ tầng & DevOps (môi trường dev)

| Thành phần | Công nghệ |
|---|---|
| Container hóa | Docker + Docker Compose |
| Quản lý phiên bản | Git + GitHub |
| CI (tùy chọn) | GitHub Actions |
| Quản lý biến môi trường | `.env` + Spring Profiles (`application-dev.yml`, `application-prod.yml`) |

---

## 3. KIẾN TRÚC TỔNG THỂ

### 3.1. Sơ đồ kiến trúc

```
                                   ┌─────────────────────────┐
                                   │        Client (Browser)  │
                                   │  React SPA + WebRTC API  │
                                   └───────────┬──────────────┘
                                               │
                     ┌─────────────────────────┼─────────────────────────┐
                     │ REST API (HTTPS)        │ WebSocket/STOMP (WSS)   │ WebRTC Media (P2P/SRTP)
                     ▼                         ▼                         ▼
        ┌─────────────────────────────────────────────────┐    ┌──────────────────┐
        │              Spring Boot Application              │    │   coturn (STUN/   │
        │  ┌───────────┐ ┌───────────┐ ┌──────────────┐    │    │   TURN server)    │
        │  │   Auth    │ │   Chat    │ │  Signaling   │    │    └──────────────────┘
        │  │  Module   │ │  Module   │ │   Module     │    │
        │  └───────────┘ └───────────┘ └──────────────┘    │
        │  ┌───────────┐ ┌───────────┐ ┌──────────────┐    │
        │  │   User    │ │Notification│ │  Presence   │    │
        │  │  Module   │ │  Module   │ │   Module     │    │
        │  └───────────┘ └───────────┘ └──────────────┘    │
        └─────────┬─────────────────────────────┬──────────┘
                   │                             │
                   ▼                             ▼
        ┌─────────────────────┐      ┌─────────────────────┐
        │   PostgreSQL 16      │      │      Redis 7          │
        │  (dữ liệu bền vững)   │      │ (presence, cache, pub/sub) │
        └─────────────────────┘      └─────────────────────┘
                   │
                   ▼
        ┌─────────────────────┐
        │  MinIO / S3           │
        │  (file, ảnh đính kèm) │
        └─────────────────────┘
```

### 3.2. Nguyên tắc kiến trúc Backend (Layered Architecture)

```
Controller Layer   (REST Controller + WebSocket Controller)
        │  nhận request, validate DTO, KHÔNG chứa business logic
        ▼
Service Layer      (Business logic, transaction boundary @Transactional)
        │  gọi Repository, gọi module khác, publish event
        ▼
Repository Layer   (Spring Data JPA Repository — truy vấn DB)
        │
        ▼
Entity/Domain Layer (JPA Entity, ánh xạ bảng DB)
```

- **DTO Pattern**: Controller không bao giờ trả trực tiếp Entity ra ngoài — luôn qua DTO (tránh lộ dữ liệu nhạy cảm, tránh lỗi lazy-loading serialize vòng lặp).
- **Mapper**: dùng MapStruct để chuyển đổi Entity <-> DTO.
- **Exception Handling tập trung**: `@RestControllerAdvice` bắt toàn bộ exception, trả về format lỗi thống nhất.
- **Event-driven nội bộ**: dùng Spring `ApplicationEventPublisher` để tách rời logic (ví dụ: gửi tin nhắn xong → publish `MessageSentEvent` → Notification module lắng nghe và xử lý riêng).

### 3.3. Module hóa hệ thống (package structure)

```
com.chatsphere
 ├── auth          (đăng ký, đăng nhập, JWT, refresh token)
 ├── user          (profile, avatar, tìm kiếm user, quan hệ bạn bè)
 ├── chat          (conversation, message, group)
 ├── signaling     (WebRTC signaling: offer/answer/ICE)
 ├── presence      (online/offline status qua Redis)
 ├── notification  (thông báo real-time)
 ├── media         (upload/download file, ảnh — tích hợp MinIO/S3)
 ├── common        (exception handler, response wrapper, utils, config)
 └── config        (SecurityConfig, WebSocketConfig, RedisConfig, CorsConfig...)
```

---

## 4. DANH SÁCH TÍNH NĂNG ĐẦY ĐỦ

### 4.1. Nhóm tính năng: Tài khoản & Xác thực (Authentication)

| # | Tính năng | Mô tả |
|---|---|---|
| 1 | Đăng ký tài khoản | Email + mật khẩu, validate định dạng, mã hóa mật khẩu bằng BCrypt |
| 2 | Xác thực email (email verification) | Gửi mã OTP/link kích hoạt qua email trước khi cho đăng nhập |
| 3 | Đăng nhập | Trả về JWT access token (thời hạn ngắn ~15 phút) + refresh token (thời hạn dài ~7 ngày) |
| 4 | Refresh token | Endpoint làm mới access token khi hết hạn mà không cần đăng nhập lại |
| 5 | Đăng xuất | Thu hồi refresh token (lưu blacklist trong Redis) |
| 6 | Quên mật khẩu / đặt lại mật khẩu | Gửi email chứa link reset có token thời hạn ngắn |
| 7 | Đổi mật khẩu | Yêu cầu xác nhận mật khẩu cũ |
| 8 | Đăng nhập bằng OAuth2 (mở rộng) | Google/Facebook login — tính năng nâng cao, làm sau khi xong core |
| 9 | Xác thực 2 lớp (2FA) (mở rộng) | TOTP qua Google Authenticator |
| 10 | Quản lý phiên đăng nhập | Xem danh sách thiết bị đã đăng nhập, đăng xuất từ xa |

### 4.2. Nhóm tính năng: Người dùng & Hồ sơ (User & Profile)

| # | Tính năng | Mô tả |
|---|---|---|
| 11 | Xem/chỉnh sửa hồ sơ cá nhân | Tên hiển thị, ảnh đại diện, bio, ngày sinh |
| 12 | Upload ảnh đại diện | Resize ảnh tự động, lưu vào object storage |
| 13 | Tìm kiếm người dùng | Theo tên, email, username (full-text search) |
| 14 | Gửi lời mời kết bạn | Gửi/nhận/chấp nhận/từ chối lời mời |
| 15 | Danh sách bạn bè | Xem danh sách, xóa bạn, chặn (block) người dùng |
| 16 | Chặn người dùng (block) | Người bị chặn không thể nhắn tin/gọi video |
| 17 | Trạng thái hoạt động (presence) | Online / Offline / Away / "Đang nhập..." hiển thị real-time |
| 18 | Cài đặt quyền riêng tư | Ai có thể xem trạng thái online, ai có thể gọi video cho mình |

### 4.3. Nhóm tính năng: Nhắn tin (Chat)

| # | Tính năng | Mô tả |
|---|---|---|
| 19 | Chat 1-1 | Nhắn tin trực tiếp giữa 2 người dùng |
| 20 | Tạo nhóm chat (group chat) | Nhiều thành viên, có tên nhóm, ảnh nhóm |
| 21 | Quản lý nhóm | Thêm/xóa thành viên, đổi tên/ảnh nhóm, rời nhóm, giải tán nhóm |
| 22 | Phân quyền trong nhóm | Admin nhóm, thành viên thường |
| 23 | Gửi tin nhắn văn bản | Hỗ trợ emoji, markdown cơ bản (in đậm, in nghiêng) |
| 24 | Gửi hình ảnh | Preview ảnh trước khi gửi, xem ảnh full-size |
| 25 | Gửi file đính kèm | PDF, docx, zip... giới hạn dung lượng |
| 26 | Gửi voice message (ghi âm) | Ghi âm trực tiếp trên trình duyệt, gửi dạng audio |
| 27 | Thu hồi tin nhắn (unsend) | Xóa tin nhắn đã gửi (trong X phút) cho cả 2 phía |
| 28 | Xóa tin nhắn phía mình | Chỉ ẩn tin nhắn ở phía người xóa |
| 29 | Chỉnh sửa tin nhắn đã gửi | Đánh dấu "đã chỉnh sửa", lưu lịch sử |
| 30 | Trả lời tin nhắn (reply) | Trích dẫn tin nhắn gốc |
| 31 | Chuyển tiếp tin nhắn (forward) | Gửi lại tin nhắn sang cuộc trò chuyện khác |
| 32 | Thả reaction (emoji react) | Like, haha, wow... trên từng tin nhắn |
| 33 | Trạng thái đã gửi/đã nhận/đã xem | Single check, double check, đã xem (giống Messenger) |
| 34 | "Đang soạn tin..." (typing indicator) | Hiển thị real-time khi đối phương đang gõ |
| 35 | Tìm kiếm tin nhắn | Tìm trong 1 cuộc trò chuyện hoặc toàn bộ |
| 36 | Ghim tin nhắn / ghim cuộc trò chuyện | Ghim thông tin quan trọng lên đầu |
| 37 | Đánh dấu chưa đọc | Đánh dấu thủ công 1 cuộc trò chuyện là "chưa đọc" |
| 38 | Cuộn vô hạn / phân trang lịch sử chat | Load tin nhắn cũ theo cuộn (infinite scroll) |
| 39 | Gửi vị trí (share location) (mở rộng) | Chia sẻ tọa độ, hiển thị bản đồ |
| 40 | Lịch hẹn nhắc nhở trong chat (mở rộng) | Đặt reminder trong 1 cuộc trò chuyện |

### 4.4. Nhóm tính năng: Thông báo (Notification)

| # | Tính năng | Mô tả |
|---|---|---|
| 41 | Thông báo tin nhắn mới real-time | Badge số tin chưa đọc, toast notification trong app |
| 42 | Web Push Notification | Nhận thông báo kể cả khi không mở tab trình duyệt (Service Worker) |
| 43 | Thông báo cuộc gọi đến | Popup toàn màn hình khi có người gọi đến |
| 44 | Tắt thông báo theo cuộc trò chuyện (mute) | Không nhận thông báo từ 1 nhóm/người cụ thể |
| 45 | Tổng hợp thông báo (notification center) | Danh sách lịch sử thông báo |

### 4.5. Nhóm tính năng: Gọi thoại/Video (Call)

| # | Tính năng | Mô tả |
|---|---|---|
| 46 | Gọi video 1-1 | Thiết lập kết nối WebRTC P2P, luồng signaling qua WebSocket |
| 47 | Gọi thoại 1-1 (audio only) | Giống video call nhưng tắt track video |
| 48 | Bật/tắt camera trong cuộc gọi | Toggle video track |
| 49 | Bật/tắt mic trong cuộc gọi | Toggle audio track |
| 50 | Chia sẻ màn hình (screen sharing) | Dùng `getDisplayMedia`, thay thế video track |
| 51 | Từ chối cuộc gọi | Gửi tín hiệu decline qua signaling |
| 52 | Nhỡ cuộc gọi (missed call) | Lưu lịch sử, hiển thị thông báo |
| 53 | Lịch sử cuộc gọi | Danh sách cuộc gọi đã thực hiện/nhận/nhỡ, thời lượng |
| 54 | Hiển thị chất lượng kết nối | Thống kê từ `RTCPeerConnection.getStats()` (mất gói, độ trễ) |
| 55 | Gọi video nhóm (group call) — **hướng mở rộng nâng cao** | Cần chuyển từ kiến trúc mesh P2P sang SFU (Selective Forwarding Unit, ví dụ mediasoup/Janus) vì mesh chỉ scale tốt đến 3-4 người |
| 56 | Ghi âm/ghi hình cuộc gọi (mở rộng) | Cần xử lý phía server (MCU) hoặc `MediaRecorder` API phía client |

### 4.6. Nhóm tính năng: Quản trị hệ thống (Admin) — mở rộng

| # | Tính năng | Mô tả |
|---|---|---|
| 57 | Dashboard thống kê | Số user, số tin nhắn/ngày, số cuộc gọi |
| 58 | Quản lý người dùng | Khóa/mở khóa tài khoản vi phạm |
| 59 | Kiểm duyệt nội dung báo cáo | Xử lý report tin nhắn/người dùng vi phạm |
| 60 | Xem log hệ thống | Audit log các hành động nhạy cảm |

**Tổng cộng: 60 tính năng**, trong đó nhóm 1-45 và 46-54 là **bắt buộc** cho bản v1 (theo lộ trình code ở file `03_CODE_ROADMAP.md`), nhóm 55, 56, 57-60 là **mở rộng** (không bắt buộc, dùng để nâng cấp dự án sau khi hoàn thành core).

---

## 5. USE CASE TỔNG QUÁT

### 5.1. Sơ đồ Use Case tổng quát (mô tả dạng text)

```
                        ┌─────────────────────────────────────┐
                        │           HỆ THỐNG CHATSPHERE          │
                        │                                       │
   (Guest) ────────────▶│  - Đăng ký                            │
                        │  - Đăng nhập                          │
                        │  - Quên mật khẩu                      │
                        │                                       │
                        │  - Quản lý hồ sơ cá nhân               │
                        │  - Quản lý bạn bè                      │
   (User) ─────────────▶│  - Nhắn tin 1-1 / nhóm                 │
                        │  - Quản lý nhóm chat                   │
                        │  - Gọi video / gọi thoại               │
                        │  - Nhận thông báo real-time            │
                        │                                       │
   (Group Admin) ──────▶│  - Quản lý thành viên nhóm              │
                        │  - Cấu hình nhóm                       │
                        │                                       │
   (System Admin) ─────▶│  - Quản lý người dùng hệ thống          │
                        │  - Xem thống kê, xử lý báo cáo          │
                        └─────────────────────────────────────┘
```

### 5.2. Danh sách Actor

| Actor | Mô tả |
|---|---|
| **Guest** | Người chưa đăng nhập |
| **User** | Người dùng đã đăng nhập (actor chính, kế thừa hầu hết use case) |
| **Group Admin** | User có vai trò quản trị trong 1 group cụ thể (là 1 "role" của User, không phải actor tách biệt về mặt kỹ thuật) |
| **System Admin** | Quản trị viên hệ thống |

### 5.3. Bảng tổng hợp Use Case theo nhóm

| Nhóm | Mã Use Case | Tên Use Case |
|---|---|---|
| Xác thực | UC-01 → UC-07 | Đăng ký, xác thực email, đăng nhập, refresh token, đăng xuất, quên MK, đổi MK |
| Người dùng | UC-08 → UC-13 | Xem/sửa hồ sơ, upload avatar, tìm user, kết bạn, chặn user, cài đặt riêng tư |
| Chat | UC-14 → UC-25 | Chat 1-1, tạo nhóm, quản lý nhóm, gửi tin nhắn/ảnh/file, thu hồi, sửa, reply, forward, react, trạng thái đọc, typing, tìm kiếm |
| Thông báo | UC-26 → UC-28 | Nhận thông báo tin nhắn, push notification, mute |
| Gọi video | UC-29 → UC-35 | Gọi video, gọi thoại, toggle cam/mic, share màn hình, từ chối, lịch sử cuộc gọi |
| Admin | UC-36 → UC-38 | Quản lý user, thống kê, xử lý report |

---

## 6. USE CASE CHI TIẾT

> Định dạng mỗi use case: **Mã | Tên | Actor | Mô tả | Điều kiện tiên quyết | Luồng chính | Luồng thay thế/ngoại lệ | Kết quả**

### UC-01: Đăng ký tài khoản

- **Actor**: Guest
- **Mô tả**: Người dùng tạo tài khoản mới bằng email và mật khẩu.
- **Điều kiện tiên quyết**: Chưa có tài khoản với email này.
- **Luồng chính**:
  1. Guest truy cập trang đăng ký, nhập email, mật khẩu, tên hiển thị.
  2. Hệ thống validate định dạng email, độ mạnh mật khẩu (>= 8 ký tự, có chữ hoa/số).
  3. Hệ thống kiểm tra email chưa tồn tại trong DB.
  4. Hệ thống mã hóa mật khẩu (BCrypt), lưu user với trạng thái `PENDING_VERIFICATION`.
  5. Hệ thống gửi email chứa mã OTP/link xác thực.
  6. Hiển thị thông báo "Vui lòng kiểm tra email để xác thực".
- **Luồng ngoại lệ**:
  - 3a. Email đã tồn tại → trả lỗi `409 Conflict`.
  - 2a. Mật khẩu không đủ mạnh → trả lỗi `400 Bad Request` kèm chi tiết.
- **Kết quả**: Tài khoản được tạo ở trạng thái chờ xác thực.

### UC-02: Xác thực email

- **Actor**: Guest (đã đăng ký)
- **Luồng chính**:
  1. User click link/nhập OTP từ email.
  2. Hệ thống kiểm tra token còn hạn (ví dụ 15 phút) và hợp lệ.
  3. Cập nhật trạng thái user thành `ACTIVE`.
- **Luồng ngoại lệ**: Token hết hạn → cho phép gửi lại email xác thực.
- **Kết quả**: Tài khoản có thể đăng nhập.

### UC-03: Đăng nhập

- **Actor**: User
- **Luồng chính**:
  1. User nhập email + mật khẩu.
  2. Hệ thống xác thực qua `AuthenticationManager` (Spring Security).
  3. Kiểm tra trạng thái tài khoản là `ACTIVE`.
  4. Sinh JWT access token (15 phút) + refresh token (7 ngày), lưu refresh token vào DB/Redis kèm thiết bị.
  5. Trả về token cho client, client lưu vào memory/httpOnly cookie.
  6. Cập nhật presence của user thành `ONLINE` trong Redis, publish sự kiện cho bạn bè.
- **Luồng ngoại lệ**:
  - Sai mật khẩu 5 lần liên tiếp → khóa tạm thời 15 phút (chống brute-force).
  - Tài khoản chưa xác thực → yêu cầu xác thực email trước.
- **Kết quả**: User nhận được token, thiết lập kết nối WebSocket.

### UC-04: Làm mới token (Refresh Token)

- **Actor**: User (hệ thống, tự động qua interceptor)
- **Luồng chính**:
  1. Client phát hiện access token hết hạn (401).
  2. Gửi refresh token đến endpoint `/api/auth/refresh`.
  3. Hệ thống kiểm tra refresh token hợp lệ, chưa bị thu hồi.
  4. Sinh access token mới, (tùy chọn) xoay vòng refresh token mới (rotation).
- **Luồng ngoại lệ**: Refresh token hết hạn/bị thu hồi → buộc đăng nhập lại.

### UC-05: Đăng xuất

- **Actor**: User
- **Luồng chính**:
  1. User bấm đăng xuất.
  2. Hệ thống thu hồi refresh token (xóa khỏi DB/Redis).
  3. Cập nhật presence thành `OFFLINE`.
  4. Đóng kết nối WebSocket.

### UC-06: Quên mật khẩu

- **Actor**: Guest
- **Luồng chính**:
  1. Nhập email tại trang "Quên mật khẩu".
  2. Hệ thống sinh token reset (thời hạn 15 phút), gửi email chứa link.
  3. User click link, nhập mật khẩu mới.
  4. Hệ thống xác thực token, cập nhật mật khẩu mới (mã hóa lại), vô hiệu hóa mọi refresh token cũ.

### UC-07: Đổi mật khẩu

- **Actor**: User (đã đăng nhập)
- **Luồng chính**:
  1. User nhập mật khẩu cũ + mật khẩu mới.
  2. Hệ thống xác thực mật khẩu cũ đúng.
  3. Cập nhật mật khẩu mới, thu hồi toàn bộ refresh token (buộc đăng nhập lại trên các thiết bị khác).

### UC-08: Xem/chỉnh sửa hồ sơ cá nhân

- **Actor**: User
- **Luồng chính**: User cập nhật tên hiển thị, bio, ngày sinh → hệ thống validate và lưu.

### UC-09: Upload ảnh đại diện

- **Actor**: User
- **Luồng chính**:
  1. User chọn ảnh (giới hạn 5MB, định dạng jpg/png/webp).
  2. Client resize/crop ảnh trước khi upload (tối ưu băng thông).
  3. Backend nhận file, validate loại file bằng magic-byte (không chỉ dựa vào extension), lưu vào MinIO/S3.
  4. Lưu URL ảnh vào DB, xóa ảnh cũ (nếu có).

### UC-10: Tìm kiếm người dùng

- **Actor**: User
- **Luồng chính**: Nhập từ khóa → hệ thống query full-text theo tên/email/username → trả danh sách kết quả phân trang.

### UC-11: Gửi/chấp nhận lời mời kết bạn

- **Actor**: User
- **Luồng chính**:
  1. User A gửi lời mời kết bạn đến User B (tạo record `FriendRequest` trạng thái `PENDING`).
  2. User B nhận thông báo real-time.
  3. User B chấp nhận → tạo quan hệ `Friendship` 2 chiều, xóa request.
  4. Hoặc User B từ chối → cập nhật trạng thái `REJECTED`.
- **Luồng thay thế**: User A hủy lời mời đã gửi trước khi B phản hồi.

### UC-12: Chặn người dùng

- **Actor**: User
- **Luồng chính**: User chọn "Chặn" trên hồ sơ người khác → tạo record `BlockedUser` → người bị chặn không thể gửi tin nhắn/gọi video, không thấy trạng thái online của nhau.

### UC-13: Cài đặt quyền riêng tư

- **Actor**: User
- **Luồng chính**: User cấu hình "Ai có thể xem trạng thái online" (Tất cả/Bạn bè/Không ai) và "Ai có thể gọi video" — lưu vào bảng `UserSettings`.

### UC-14: Bắt đầu cuộc trò chuyện 1-1

- **Actor**: User
- **Luồng chính**:
  1. User chọn 1 người bạn từ danh sách.
  2. Hệ thống kiểm tra đã có `Conversation` loại `DIRECT` giữa 2 người chưa — nếu chưa, tạo mới.
  3. Chuyển đến giao diện chat.

### UC-15: Tạo nhóm chat

- **Actor**: User
- **Luồng chính**:
  1. User chọn danh sách thành viên (>= 2 người khác), đặt tên nhóm.
  2. Hệ thống tạo `Conversation` loại `GROUP`, tạo `ConversationParticipant` cho từng thành viên, người tạo mặc định là `ADMIN`.
  3. Gửi tin nhắn hệ thống "X đã tạo nhóm" vào cuộc trò chuyện.

### UC-16: Quản lý thành viên nhóm

- **Actor**: Group Admin
- **Luồng chính**: Thêm thành viên mới / Xóa thành viên / Chuyển quyền admin / Đổi tên-ảnh nhóm.
- **Luồng ngoại lệ**: User thường cố thực hiện hành động admin → trả lỗi `403 Forbidden`.

### UC-17: Rời nhóm / Giải tán nhóm

- **Actor**: User (rời nhóm) / Group Admin (giải tán)
- **Luồng chính**: User rời nhóm → xóa `ConversationParticipant` tương ứng, gửi tin nhắn hệ thống thông báo. Nếu Admin duy nhất rời nhóm → hệ thống tự động chuyển quyền admin cho thành viên còn lại theo thứ tự tham gia sớm nhất.

### UC-18: Gửi tin nhắn văn bản

- **Actor**: User
- **Luồng chính**:
  1. User nhập nội dung, nhấn gửi.
  2. Client gửi message qua STOMP đến `/app/chat.sendMessage`.
  3. Server validate người gửi là thành viên hợp lệ của conversation, chưa bị block.
  4. Lưu `Message` vào DB (trạng thái `SENT`).
  5. Publish message đến topic `/topic/conversation/{id}` — tất cả client đang subscribe nhận ngay lập tức.
  6. Cập nhật `lastMessage`, `updatedAt` của conversation (phục vụ sắp xếp danh sách chat).
  7. Trigger notification cho các thành viên offline.

### UC-19: Gửi hình ảnh / file đính kèm

- **Actor**: User
- **Luồng chính**:
  1. User chọn file → upload qua REST endpoint `/api/media/upload` trước (trả về URL).
  2. Gửi message với `type=IMAGE/FILE` kèm URL vừa upload qua kênh WebSocket như UC-18.
- **Ràng buộc**: Giới hạn dung lượng (ảnh 10MB, file 25MB), kiểm tra virus scan (tùy chọn nâng cao).

### UC-20: Thu hồi tin nhắn (Unsend)

- **Actor**: User (chủ tin nhắn)
- **Luồng chính**:
  1. User chọn "Thu hồi" trong vòng 5 phút kể từ khi gửi.
  2. Server kiểm tra quyền sở hữu + thời gian hợp lệ.
  3. Cập nhật `status = RECALLED`, nội dung bị xóa khỏi response, giữ lại record để không bị lệch thứ tự.
  4. Broadcast sự kiện `MESSAGE_RECALLED` để các client cập nhật giao diện thành "Tin nhắn đã được thu hồi".

### UC-21: Chỉnh sửa tin nhắn

- **Actor**: User (chủ tin nhắn)
- **Luồng chính**: Tương tự UC-20 nhưng cập nhật nội dung mới, đánh dấu `isEdited=true`, lưu `MessageEditHistory` (mở rộng).

### UC-22: Trả lời / Chuyển tiếp tin nhắn

- **Actor**: User
- **Luồng chính (Reply)**: Gửi message mới kèm `replyToMessageId` tham chiếu.
- **Luồng chính (Forward)**: Chọn 1 tin nhắn có sẵn → chọn cuộc trò chuyện đích → tạo message mới ở đích với `forwardedFromMessageId`.

### UC-23: Thả reaction

- **Actor**: User
- **Luồng chính**: User chọn emoji trên 1 tin nhắn → tạo/cập nhật record `MessageReaction` (unique theo message+user, đổi emoji nếu react lại) → broadcast realtime.

### UC-24: Trạng thái đã gửi/đã nhận/đã xem & Typing indicator

- **Actor**: User (hệ thống tự động)
- **Luồng chính**:
  - Khi client nhận message qua socket → gửi ACK `DELIVERED`.
  - Khi user mở cuộc trò chuyện và cuộn tới tin nhắn → gửi sự kiện `READ` kèm `lastReadMessageId`, lưu vào `ConversationParticipant.lastReadMessageId`.
  - Typing: client gửi sự kiện `TYPING_START`/`TYPING_STOP` qua kênh riêng `/app/chat.typing`, server broadcast cho các thành viên khác trong conversation (không lưu DB, chỉ real-time, có thể tự hết hạn sau vài giây phía client).

### UC-25: Tìm kiếm tin nhắn

- **Actor**: User
- **Luồng chính**: Nhập từ khóa trong 1 conversation hoặc toàn bộ → query full-text search (PostgreSQL `tsvector`) → trả kết quả kèm context (jump-to-message).

### UC-26: Nhận thông báo tin nhắn mới

- **Actor**: User
- **Luồng chính**: Khi có message mới mà user không đang mở đúng conversation đó → tăng badge số chưa đọc, hiển thị toast trong app.

### UC-27: Web Push Notification

- **Actor**: User
- **Điều kiện tiên quyết**: User đã cấp quyền notification cho trình duyệt, đã đăng ký Service Worker + Push Subscription.
- **Luồng chính**: Backend dùng Web Push Protocol (VAPID key) gửi thông báo đến endpoint push của trình duyệt kể cả khi tab đã đóng.

### UC-28: Tắt thông báo cuộc trò chuyện (Mute)

- **Actor**: User
- **Luồng chính**: User chọn mute 1 conversation (vĩnh viễn hoặc theo thời gian: 1h/8h/1 tuần) → lưu vào `ConversationParticipant.mutedUntil`.

### UC-29: Gọi video 1-1 (Luồng WebRTC signaling đầy đủ)

- **Actor**: User (caller), User (callee)
- **Điều kiện tiên quyết**: Cả 2 đều online, callee không chặn caller, callee cho phép nhận cuộc gọi (theo UC-13).
- **Luồng chính**:
  1. Caller nhấn "Gọi video" → client lấy local media (`getUserMedia`).
  2. Client gửi sự kiện `CALL_INVITE` qua WebSocket đến server, kèm `calleeId`, `conversationId`, `callType=VIDEO`.
  3. Server kiểm tra điều kiện tiên quyết, tạo record `CallSession` trạng thái `RINGING`, forward `CALL_INVITE` đến callee qua kênh riêng `/user/{calleeId}/queue/call`.
  4. Callee nhận popup cuộc gọi đến → chấp nhận.
  5. Callee gửi `CALL_ACCEPT` → server forward về caller.
  6. Caller tạo `RTCPeerConnection`, tạo SDP Offer, gửi `SDP_OFFER` qua server đến callee.
  7. Callee nhận Offer, tạo `RTCPeerConnection`, set remote description, tạo SDP Answer, gửi `SDP_ANSWER` về caller qua server.
  8. Cả 2 bên trao đổi `ICE_CANDIDATE` liên tục qua server cho đến khi tìm được đường kết nối tối ưu (trực tiếp qua STUN hoặc relay qua TURN).
  9. Kết nối P2P (SRTP) được thiết lập trực tiếp giữa 2 trình duyệt — **server Spring Boot không truyền media, chỉ làm trung gian tín hiệu**.
  10. Cập nhật `CallSession.status = ONGOING`, `startedAt`.
  11. Khi 1 bên kết thúc cuộc gọi → gửi `CALL_END` → server cập nhật `status=ENDED`, `endedAt`, tính `duration`, forward cho bên còn lại để đóng kết nối.
- **Luồng ngoại lệ**:
  - Callee không phản hồi trong 30 giây → server tự động gửi `CALL_TIMEOUT`, cập nhật `status=MISSED`.
  - Callee từ chối → `CALL_DECLINE`, `status=DECLINED`.
  - Không tìm được đường kết nối ICE (cả STUN lẫn TURN thất bại, hiếm) → `status=FAILED`, hiển thị lỗi cho cả 2 phía.
- **Kết quả**: Cuộc gọi được thiết lập P2P thành công hoặc thất bại có ghi nhận lịch sử.

### UC-30: Gọi thoại 1-1

- Giống UC-29 nhưng `callType=AUDIO`, không gọi `getUserMedia({video:true})`, chỉ lấy audio track.

### UC-31: Bật/tắt camera, mic trong cuộc gọi

- **Actor**: User (đang trong cuộc gọi)
- **Luồng chính**: Toggle `track.enabled = false/true` trên local stream — không cần renegotiate SDP vì track vẫn tồn tại, chỉ tắt tạm thời. Gửi sự kiện `MEDIA_STATE_CHANGED` để hiển thị icon trạng thái cho đối phương.

### UC-32: Chia sẻ màn hình

- **Actor**: User (đang trong cuộc gọi)
- **Luồng chính**:
  1. User bấm "Chia sẻ màn hình" → `navigator.mediaDevices.getDisplayMedia()`.
  2. Dùng `RTCRtpSender.replaceTrack()` để thay thế video track hiện tại bằng track màn hình — **không cần renegotiate lại toàn bộ kết nối**.
  3. Khi dừng chia sẻ → `replaceTrack()` lại về camera track ban đầu.

### UC-33: Từ chối cuộc gọi

- Đã mô tả trong luồng ngoại lệ của UC-29.

### UC-34: Lịch sử cuộc gọi

- **Actor**: User
- **Luồng chính**: Xem danh sách `CallSession` liên quan đến mình, lọc theo loại (gọi đi/gọi đến/nhỡ), sắp xếp theo thời gian giảm dần.

### UC-35: Hiển thị chất lượng kết nối

- **Actor**: User (trong cuộc gọi)
- **Luồng chính**: Client định kỳ gọi `peerConnection.getStats()`, tính toán packet loss/jitter/round-trip time, hiển thị icon chất lượng (tốt/trung bình/yếu).

### UC-36 → UC-38: Quản trị hệ thống (mở rộng)

- **UC-36 Quản lý người dùng**: Admin khóa/mở khóa tài khoản vi phạm.
- **UC-37 Xem thống kê**: Dashboard số liệu tổng quan.
- **UC-38 Xử lý report**: Admin xem danh sách báo cáo vi phạm, quyết định xử lý.

---

## 7. THIẾT KẾ DATABASE

### 7.1. Nguyên tắc thiết kế

- Chuẩn hóa dữ liệu tới **3NF** (Third Normal Form) cho các bảng nghiệp vụ chính, tránh trùng lặp dữ liệu.
- Dùng **UUID** làm khóa chính cho các bảng chính (User, Conversation, Message...) để tránh lộ thông tin số lượng record qua ID tuần tự, và thuận tiện khi cần đồng bộ/sharding sau này.
- Mọi bảng đều có `created_at`, `updated_at` (audit columns cơ bản).
- Sử dụng **soft delete** (`deleted_at` hoặc `is_deleted`) cho các bảng dữ liệu quan trọng (User, Message, Conversation) thay vì xóa cứng.
- Đánh **index** đầy đủ trên các cột dùng để JOIN, WHERE, ORDER BY thường xuyên (đặc biệt `conversation_id`, `created_at` trong bảng Message vì đây là bảng lớn nhất, tăng trưởng nhanh nhất).

### 7.2. Sơ đồ ERD (mô tả dạng text)

```
users ──────────────┬───────────────< friendships >───────────────── users
  │                 │
  │                 ├───────────────< friend_requests >────────────── users
  │                 │
  │                 ├───────────────< blocked_users >────────────────  users
  │                 │
  │                 └── 1:1 ── user_settings
  │
  ├──< conversation_participants >── conversations
  │            │                          │
  │            │                          ├──< messages >──┐
  │            │                          │                 ├── message_reactions
  │            │                          │                 ├── message_attachments
  │            │                          │                 └── (self-ref) reply_to_message_id
  │            │                          │
  │            └── (composite: user_id + conversation_id)
  │
  ├──< call_sessions >── (caller_id, callee_id đều FK -> users)
  │
  ├──< notifications >
  │
  ├──< refresh_tokens >
  │
  └──< push_subscriptions >
```

### 7.3. Chi tiết từng bảng

#### 7.3.1. `users`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| email | VARCHAR(255) | UNIQUE, NOT NULL | Email đăng nhập |
| password_hash | VARCHAR(255) | NOT NULL | Mật khẩu đã mã hóa BCrypt |
| username | VARCHAR(50) | UNIQUE, NOT NULL | Tên định danh duy nhất (dùng để tìm kiếm, mention) |
| display_name | VARCHAR(100) | NOT NULL | Tên hiển thị |
| avatar_url | VARCHAR(500) | NULL | URL ảnh đại diện |
| bio | VARCHAR(255) | NULL | Tiểu sử ngắn |
| date_of_birth | DATE | NULL | Ngày sinh |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING_VERIFICATION' | `PENDING_VERIFICATION`, `ACTIVE`, `LOCKED`, `DEACTIVATED` |
| role | VARCHAR(20) | NOT NULL, DEFAULT 'USER' | `USER`, `ADMIN` |
| last_login_at | TIMESTAMP | NULL | Lần đăng nhập gần nhất |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |
| deleted_at | TIMESTAMP | NULL | Soft delete |

**Index**: `idx_users_email` (unique), `idx_users_username` (unique), full-text index trên `display_name`.

#### 7.3.2. `user_settings`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| user_id | UUID | PK, FK -> users.id | Quan hệ 1-1 với users |
| online_visibility | VARCHAR(20) | DEFAULT 'EVERYONE' | `EVERYONE`, `FRIENDS_ONLY`, `NOBODY` |
| call_permission | VARCHAR(20) | DEFAULT 'EVERYONE' | `EVERYONE`, `FRIENDS_ONLY`, `NOBODY` |
| notification_enabled | BOOLEAN | DEFAULT TRUE | |
| updated_at | TIMESTAMP | NOT NULL | |

#### 7.3.3. `friend_requests`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| sender_id | UUID | FK -> users.id, NOT NULL | |
| receiver_id | UUID | FK -> users.id, NOT NULL | |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PENDING' | `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED` |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**Ràng buộc**: UNIQUE (`sender_id`, `receiver_id`) khi `status='PENDING'` (tránh gửi trùng lời mời).

#### 7.3.4. `friendships`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| user_id_1 | UUID | FK -> users.id, NOT NULL | Quy ước `user_id_1 < user_id_2` để tránh lưu trùng 2 chiều |
| user_id_2 | UUID | FK -> users.id, NOT NULL | |
| created_at | TIMESTAMP | NOT NULL | Ngày kết bạn |

**Index**: UNIQUE (`user_id_1`, `user_id_2`).

#### 7.3.5. `blocked_users`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| blocker_id | UUID | FK -> users.id, NOT NULL | Người chặn |
| blocked_id | UUID | FK -> users.id, NOT NULL | Người bị chặn |
| created_at | TIMESTAMP | NOT NULL | |

**Index**: UNIQUE (`blocker_id`, `blocked_id`).

#### 7.3.6. `conversations`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| type | VARCHAR(10) | NOT NULL | `DIRECT`, `GROUP` |
| name | VARCHAR(100) | NULL | Tên nhóm (NULL với DIRECT) |
| avatar_url | VARCHAR(500) | NULL | Ảnh nhóm |
| created_by | UUID | FK -> users.id | Người tạo |
| last_message_id | UUID | FK -> messages.id, NULL | Denormalize để query danh sách chat nhanh (tránh JOIN/subquery mỗi lần load) |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | Cập nhật mỗi khi có tin nhắn mới — dùng để sắp xếp danh sách hội thoại |

**Index**: `idx_conversations_updated_at`.

#### 7.3.7. `conversation_participants`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| conversation_id | UUID | FK -> conversations.id, NOT NULL | |
| user_id | UUID | FK -> users.id, NOT NULL | |
| role | VARCHAR(20) | DEFAULT 'MEMBER' | `ADMIN`, `MEMBER` (chỉ có ý nghĩa với GROUP) |
| last_read_message_id | UUID | FK -> messages.id, NULL | Phục vụ tính unread count |
| muted_until | TIMESTAMP | NULL | NULL = không mute |
| joined_at | TIMESTAMP | NOT NULL | |
| left_at | TIMESTAMP | NULL | Soft leave — giữ lại lịch sử |

**Index**: UNIQUE (`conversation_id`, `user_id`) khi `left_at IS NULL`; `idx_participant_user_id` (để query "danh sách hội thoại của tôi" nhanh).

#### 7.3.8. `messages`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| conversation_id | UUID | FK -> conversations.id, NOT NULL | |
| sender_id | UUID | FK -> users.id, NOT NULL | |
| type | VARCHAR(20) | NOT NULL | `TEXT`, `IMAGE`, `FILE`, `VOICE`, `SYSTEM` |
| content | TEXT | NULL | Nội dung text (NULL nếu chỉ có attachment) |
| reply_to_message_id | UUID | FK -> messages.id, NULL | Self-reference cho tính năng reply |
| forwarded_from_message_id | UUID | FK -> messages.id, NULL | Self-reference cho tính năng forward |
| status | VARCHAR(20) | NOT NULL DEFAULT 'SENT' | `SENT`, `DELIVERED`, `READ`, `RECALLED` |
| is_edited | BOOLEAN | DEFAULT FALSE | |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |
| deleted_at | TIMESTAMP | NULL | Soft delete khi user tự xóa phía mình (kết hợp bảng `message_deletions` bên dưới) |

**Index quan trọng nhất hệ thống**: composite index `(conversation_id, created_at DESC)` — phục vụ query phổ biến nhất "lấy N tin nhắn gần nhất của 1 cuộc trò chuyện, phân trang theo cursor". Ngoài ra `tsvector` GIN index trên `content` để full-text search (UC-25).

#### 7.3.9. `message_deletions` (xóa phía riêng từng người — UC-28 nhóm chat)

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| message_id | UUID | FK -> messages.id, NOT NULL | |
| user_id | UUID | FK -> users.id, NOT NULL | Người đã xóa tin nhắn này phía mình |
| deleted_at | TIMESTAMP | NOT NULL | |

**Index**: UNIQUE (`message_id`, `user_id`).

#### 7.3.10. `message_attachments`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| message_id | UUID | FK -> messages.id, NOT NULL | |
| file_url | VARCHAR(500) | NOT NULL | URL trên object storage |
| file_name | VARCHAR(255) | NOT NULL | Tên file gốc |
| file_type | VARCHAR(100) | NOT NULL | MIME type |
| file_size | BIGINT | NOT NULL | Bytes |
| thumbnail_url | VARCHAR(500) | NULL | Ảnh thu nhỏ (nếu là ảnh/video) |
| created_at | TIMESTAMP | NOT NULL | |

#### 7.3.11. `message_reactions`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| message_id | UUID | FK -> messages.id, NOT NULL | |
| user_id | UUID | FK -> users.id, NOT NULL | |
| emoji | VARCHAR(10) | NOT NULL | Unicode emoji |
| created_at | TIMESTAMP | NOT NULL | |

**Index**: UNIQUE (`message_id`, `user_id`) — 1 user chỉ có 1 reaction/tin nhắn, react lại sẽ update.

#### 7.3.12. `call_sessions`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| conversation_id | UUID | FK -> conversations.id, NOT NULL | |
| caller_id | UUID | FK -> users.id, NOT NULL | |
| callee_id | UUID | FK -> users.id, NOT NULL | (v1 chỉ hỗ trợ 1-1, group call là mở rộng dùng bảng `call_participants` riêng) |
| call_type | VARCHAR(10) | NOT NULL | `AUDIO`, `VIDEO` |
| status | VARCHAR(20) | NOT NULL | `RINGING`, `ONGOING`, `ENDED`, `MISSED`, `DECLINED`, `FAILED` |
| started_at | TIMESTAMP | NULL | Thời điểm callee chấp nhận |
| ended_at | TIMESTAMP | NULL | |
| duration_seconds | INT | NULL | Tính toán khi kết thúc |
| created_at | TIMESTAMP | NOT NULL | Thời điểm bắt đầu gọi (invite) |

**Index**: `idx_call_caller`, `idx_call_callee`, `idx_call_created_at`.

#### 7.3.13. `notifications`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| user_id | UUID | FK -> users.id, NOT NULL | Người nhận thông báo |
| type | VARCHAR(30) | NOT NULL | `NEW_MESSAGE`, `FRIEND_REQUEST`, `MISSED_CALL`, `MENTIONED`... |
| reference_id | UUID | NULL | ID đối tượng liên quan (message_id, friend_request_id...) |
| content | VARCHAR(500) | NOT NULL | Nội dung hiển thị |
| is_read | BOOLEAN | DEFAULT FALSE | |
| created_at | TIMESTAMP | NOT NULL | |

**Index**: `idx_notifications_user_id_created_at`.

#### 7.3.14. `refresh_tokens`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| user_id | UUID | FK -> users.id, NOT NULL | |
| token_hash | VARCHAR(255) | NOT NULL | Lưu hash, không lưu raw token |
| device_info | VARCHAR(255) | NULL | User-Agent / tên thiết bị |
| expires_at | TIMESTAMP | NOT NULL | |
| revoked | BOOLEAN | DEFAULT FALSE | |
| created_at | TIMESTAMP | NOT NULL | |

#### 7.3.15. `push_subscriptions`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| user_id | UUID | FK -> users.id, NOT NULL | |
| endpoint | VARCHAR(500) | NOT NULL | Push endpoint của trình duyệt |
| p256dh_key | VARCHAR(255) | NOT NULL | |
| auth_key | VARCHAR(255) | NOT NULL | |
| created_at | TIMESTAMP | NOT NULL | |

### 7.4. Ghi chú thiết kế quan trọng

- **Vì sao không dùng 1 bảng `messages` join trực tiếp participant?** Vì tách `conversation_participants` cho phép mỗi user có trạng thái đọc/mute riêng trên cùng 1 conversation mà không phải sửa bảng `messages` (bảng lớn nhất, ghi liên tục).
- **Vì sao denormalize `last_message_id` vào `conversations`?** Trang danh sách hội thoại là màn hình được load nhiều nhất — nếu mỗi lần phải `SELECT ... FROM messages WHERE conversation_id=? ORDER BY created_at DESC LIMIT 1` cho từng conversation sẽ rất tốn kém khi user có hàng trăm cuộc trò chuyện. Đánh đổi là phải cập nhật thêm 1 field khi gửi tin nhắn (chấp nhận được).
- **Xử lý câu hỏi "tin nhắn chưa đọc là bao nhiêu?"**: so sánh `created_at` của message mới nhất với `last_read_message_id` tương ứng — có thể cache số đếm này trong Redis để tránh COUNT(*) liên tục.

---

## 8. THIẾT KẾ API

### 8.1. Quy ước chung

- Base URL: `/api/v1`
- Định dạng response chuẩn hóa:
```json
{
  "success": true,
  "data": { },
  "error": null,
  "timestamp": "2026-09-03T10:00:00Z"
}
```
- Lỗi:
```json
{
  "success": false,
  "data": null,
  "error": { "code": "USER_NOT_FOUND", "message": "Không tìm thấy người dùng" },
  "timestamp": "2026-09-03T10:00:00Z"
}
```
- Phân trang dùng **cursor-based pagination** cho tin nhắn (hiệu quả hơn offset-based khi dữ liệu lớn và liên tục thay đổi): `?cursor=<messageId>&limit=30`.
- Authorization header: `Authorization: Bearer <access_token>`.

### 8.2. Danh sách endpoint chính (tóm tắt)

| Method | Endpoint | Mô tả | UC liên quan |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Đăng ký | UC-01 |
| POST | `/api/v1/auth/verify-email` | Xác thực email | UC-02 |
| POST | `/api/v1/auth/login` | Đăng nhập | UC-03 |
| POST | `/api/v1/auth/refresh` | Làm mới token | UC-04 |
| POST | `/api/v1/auth/logout` | Đăng xuất | UC-05 |
| POST | `/api/v1/auth/forgot-password` | Quên mật khẩu | UC-06 |
| POST | `/api/v1/auth/reset-password` | Đặt lại mật khẩu | UC-06 |
| PUT | `/api/v1/auth/change-password` | Đổi mật khẩu | UC-07 |
| GET | `/api/v1/users/me` | Lấy hồ sơ bản thân | UC-08 |
| PUT | `/api/v1/users/me` | Cập nhật hồ sơ | UC-08 |
| POST | `/api/v1/users/me/avatar` | Upload avatar | UC-09 |
| GET | `/api/v1/users/search?q=` | Tìm kiếm user | UC-10 |
| POST | `/api/v1/friend-requests` | Gửi lời mời kết bạn | UC-11 |
| PUT | `/api/v1/friend-requests/{id}/accept` | Chấp nhận | UC-11 |
| PUT | `/api/v1/friend-requests/{id}/reject` | Từ chối | UC-11 |
| GET | `/api/v1/friends` | Danh sách bạn bè | UC-11 |
| POST | `/api/v1/users/{id}/block` | Chặn người dùng | UC-12 |
| PUT | `/api/v1/users/me/settings` | Cài đặt riêng tư | UC-13 |
| GET | `/api/v1/conversations` | Danh sách hội thoại | UC-14 |
| POST | `/api/v1/conversations/direct` | Tạo/lấy hội thoại 1-1 | UC-14 |
| POST | `/api/v1/conversations/group` | Tạo nhóm | UC-15 |
| PUT | `/api/v1/conversations/{id}` | Đổi tên/ảnh nhóm | UC-16 |
| POST | `/api/v1/conversations/{id}/members` | Thêm thành viên | UC-16 |
| DELETE | `/api/v1/conversations/{id}/members/{userId}` | Xóa thành viên | UC-16 |
| POST | `/api/v1/conversations/{id}/leave` | Rời nhóm | UC-17 |
| GET | `/api/v1/conversations/{id}/messages?cursor=&limit=` | Lấy lịch sử tin nhắn | UC-18 |
| POST | `/api/v1/media/upload` | Upload file/ảnh | UC-19 |
| PUT | `/api/v1/messages/{id}/recall` | Thu hồi tin nhắn | UC-20 |
| PUT | `/api/v1/messages/{id}` | Sửa tin nhắn | UC-21 |
| POST | `/api/v1/messages/{id}/forward` | Chuyển tiếp | UC-22 |
| POST | `/api/v1/messages/{id}/reactions` | Thả reaction | UC-23 |
| GET | `/api/v1/conversations/{id}/search?q=` | Tìm kiếm tin nhắn | UC-25 |
| GET | `/api/v1/notifications` | Danh sách thông báo | UC-26 |
| POST | `/api/v1/push-subscriptions` | Đăng ký push | UC-27 |
| PUT | `/api/v1/conversations/{id}/mute` | Mute hội thoại | UC-28 |
| GET | `/api/v1/calls/history` | Lịch sử cuộc gọi | UC-34 |

### 8.3. WebSocket Destinations (STOMP)

| Loại | Destination | Chiều | Mô tả |
|---|---|---|---|
| Client gửi | `/app/chat.sendMessage` | Client → Server | Gửi tin nhắn mới |
| Client gửi | `/app/chat.typing` | Client → Server | Báo đang gõ |
| Client gửi | `/app/call.invite` | Client → Server | Khởi tạo cuộc gọi |
| Client gửi | `/app/call.signal` | Client → Server | Gửi SDP/ICE candidate |
| Client gửi | `/app/call.end` | Client → Server | Kết thúc cuộc gọi |
| Server broadcast | `/topic/conversation/{conversationId}` | Server → nhiều Client | Tin nhắn mới, typing, reaction trong 1 hội thoại |
| Server gửi riêng | `/user/{userId}/queue/notifications` | Server → 1 Client | Thông báo cá nhân |
| Server gửi riêng | `/user/{userId}/queue/call` | Server → 1 Client | Tín hiệu cuộc gọi (invite/answer/ICE/end) |
| Server gửi riêng | `/user/{userId}/queue/presence` | Server → 1 Client | Cập nhật trạng thái online bạn bè |

---

## 9. THIẾT KẾ LUỒNG WEBSOCKET & SIGNALING

### 9.1. Vòng đời kết nối WebSocket

1. Client kết nối `wss://api.chatsphere.com/ws` kèm JWT trong header `Authorization` (qua STOMP CONNECT frame).
2. `ChannelInterceptor` phía server xác thực JWT tại thời điểm `CONNECT`, gắn `Principal` (userId) vào session.
3. Server lưu mapping `userId -> sessionId` vào Redis, publish sự kiện "user online" cho danh sách bạn bè.
4. Client subscribe các topic cần thiết: hội thoại đang mở, queue cá nhân (notification, call).
5. Khi client ngắt kết nối (đóng tab, mất mạng), sự kiện `SessionDisconnectEvent` được bắt → xóa mapping Redis, publish "user offline" (có debounce ~10s để tránh nhấp nháy online/offline khi mất mạng chập chờn).

### 9.2. Luồng Signaling WebRTC chi tiết (kèm sequence)

```
Caller                      Server (Spring Boot)                 Callee
  │                               │                                  │
  │──CALL_INVITE─────────────────▶│                                  │
  │                               │──validate, tạo CallSession───────│
  │                               │──CALL_INVITE────────────────────▶│
  │                               │                                  │──(popup accept/decline)
  │                               │◀─────────────────────CALL_ACCEPT─│
  │◀──────────────────CALL_ACCEPT─│                                  │
  │                               │                                  │
  │──(tạo RTCPeerConnection)      │                                  │
  │──SDP_OFFER────────────────────▶│──SDP_OFFER──────────────────────▶│
  │                               │                                  │──(tạo RTCPeerConnection,
  │                               │                                  │   setRemoteDescription)
  │                               │◀────────────────────SDP_ANSWER──│
  │◀──────────────────SDP_ANSWER──│                                  │
  │                               │                                  │
  │──ICE_CANDIDATE (nhiều lần)───▶│──ICE_CANDIDATE──────────────────▶│
  │◀─────────────────ICE_CANDIDATE│◀────────────────ICE_CANDIDATE────│
  │                               │                                  │
  │◀═══════════════ Kết nối P2P trực tiếp (SRTP media) ═════════════▶│
  │                               │                                  │
  │──CALL_END─────────────────────▶│──CALL_END───────────────────────▶│
```

### 9.3. Vai trò của STUN/TURN

- **STUN**: giúp mỗi peer biết được địa chỉ IP:port public của mình sau NAT (kỹ thuật "hole punching"). Đủ dùng khi cả 2 bên ở NAT loại "cone" (phổ biến ở mạng gia đình/wifi thường).
- **TURN**: khi 1 hoặc cả 2 bên ở sau NAT đối xứng (symmetric NAT — phổ biến ở mạng doanh nghiệp/di động 4G có firewall chặt), kết nối trực tiếp P2P sẽ thất bại. TURN server đóng vai trò relay toàn bộ media — lúc này media không còn "P2P" nữa mà đi qua server TURN. Đây là lý do **bắt buộc phải có TURN server khi lên production**, nếu không tỷ lệ cuộc gọi thất bại có thể lên đến 15-30% tùy loại mạng người dùng.
- Định dạng cấu hình ICE server phía client:
```json
[
  { "urls": "stun:stun.l.google.com:19302" },
  { "urls": "turn:turn.chatsphere.com:3478", "username": "...", "credential": "..." }
]
```
- **Bảo mật TURN**: dùng credential ngắn hạn (time-limited, sinh bằng HMAC-SHA1 theo chuẩn `coturn` REST API) thay vì user/pass tĩnh, để tránh lộ thông tin bị lạm dụng làm relay miễn phí.

---

## 10. BẢO MẬT

| Hạng mục | Biện pháp |
|---|---|
| Mật khẩu | BCrypt (cost factor 12), không bao giờ trả password_hash ra API |
| Token | JWT ký bằng HS256/RS256, access token thời hạn ngắn, refresh token rotation |
| Chống Brute-force | Rate limit đăng nhập theo IP + theo tài khoản (Redis counter), khóa tạm sau 5 lần sai |
| CORS | Chỉ whitelist domain frontend chính thức |
| Input validation | Bean Validation ở DTO, kiểm tra thêm ở Service layer với business rule |
| SQL Injection | Dùng JPA/Hibernate với parameterized query, không nối chuỗi SQL thủ công |
| XSS | Sanitize nội dung tin nhắn khi hiển thị phía frontend (React tự escape mặc định, nhưng cẩn trọng khi `dangerouslySetInnerHTML`) |
| File upload | Kiểm tra MIME type qua magic byte, giới hạn kích thước, quét virus (ClamAV — mở rộng), lưu ngoài webroot (object storage) |
| WebSocket Auth | Xác thực JWT ngay tại STOMP CONNECT, không cho phép kết nối anonymous |
| Authorization | Kiểm tra quyền truy cập ở Service layer cho từng conversation/message (user chỉ thao tác được trên tài nguyên mình có quyền) |
| HTTPS/WSS | Bắt buộc ở production, không cho phép HTTP/WS thuần |
| TURN credential | Time-limited credential, không dùng static username/password |
| Audit log | Ghi log các hành động nhạy cảm (đổi mật khẩu, xóa tài khoản, admin action) |
| Secrets management | Không hardcode secret trong code, dùng biến môi trường / vault khi production |

---

## 11. PHI CHỨC NĂNG (NON-FUNCTIONAL REQUIREMENTS)

| Loại | Yêu cầu |
|---|---|
| Hiệu năng | Tin nhắn phải hiển thị ở phía nhận trong vòng < 500ms trong điều kiện mạng bình thường |
| Khả năng mở rộng | Kiến trúc phải cho phép chạy nhiều instance Spring Boot phía sau load balancer (dùng Redis Pub/Sub làm STOMP relay để đồng bộ giữa các instance — xem `04_PRODUCTION_DEPLOYMENT.md`) |
| Độ tin cậy | Tin nhắn phải được lưu DB trước khi xác nhận gửi thành công (không mất dữ liệu khi server restart) |
| Khả năng bảo trì | Code tuân thủ layered architecture, có test coverage tối thiểu 60% cho Service layer |
| Khả năng quan sát (Observability) | Log có cấu trúc (structured logging), tích hợp health check endpoint (`/actuator/health`) |
| Tương thích trình duyệt | Chrome, Firefox, Edge, Safari (bản mới nhất) — WebRTC cần kiểm tra kỹ tương thích Safari (có một số khác biệt) |
| Khả năng học tập (đặc thù dự án) | Code phải có comment giải thích rõ các đoạn logic phức tạp (đặc biệt phần signaling, ICE handling) để phục vụ mục đích tự học |

---

*Hết tài liệu 01_SYSTEM_DESIGN.md — tiếp theo xem `02_SETUP_GUIDE.md` để bắt đầu cài đặt môi trường.*
