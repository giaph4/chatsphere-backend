# API REFERENCE — AUTH MODULE

**Base URL**: `http://localhost:8080` (dev) · **Prefix**: `/api/v1/auth`
**Swagger UI**: `/swagger-ui.html` · **OpenAPI JSON**: `/v3/api-docs`

Tài liệu này mô tả đầy đủ 8 endpoint của module Auth: request/response schema, validation, mã lỗi,
ví dụ `curl`, và các ca biên cần lưu ý. Đi kèm `06_PHASE1_AUTH_REPORT.md` (kiến trúc, luồng, lý thuyết).

---

## MỤC LỤC

1. [Quy ước chung](#1-quy-ước-chung)
2. [Xác thực (Authentication)](#2-xác-thực-authentication)
3. `POST /register` — [Đăng ký](#31-post-apiv1authregister)
4. `POST /verify-email` — [Xác thực email](#32-post-apiv1authverify-email)
5. `POST /login` — [Đăng nhập](#33-post-apiv1authlogin)
6. `POST /refresh` — [Làm mới token](#34-post-apiv1authrefresh)
7. `POST /logout` — [Đăng xuất](#35-post-apiv1authlogout)
8. `POST /forgot-password` — [Quên mật khẩu](#36-post-apiv1authforgot-password)
9. `POST /reset-password` — [Đặt lại mật khẩu](#37-post-apiv1authreset-password)
10. `PUT /change-password` — [Đổi mật khẩu](#38-put-apiv1authchange-password)
11. [Bảng tổng hợp mã lỗi](#4-bảng-tổng-hợp-mã-lỗi)
12. [Kịch bản test end-to-end](#5-kịch-bản-test-end-to-end)
13. [Postman / curl collection](#6-postman--curl-collection)

---

## 1. QUY ƯỚC CHUNG

### 1.1. Phong bì response

Mọi response — thành công lẫn thất bại — đều bọc trong cùng một cấu trúc:

```jsonc
{
  "success": true,        // boolean — luôn có
  "data": { ... } | null, // payload khi thành công, null khi lỗi
  "error": null | {       // null khi thành công
    "code": "INVALID_CREDENTIALS",   // hằng số ổn định — FE switch/case theo cái này
    "message": "Email hoặc mật khẩu không đúng"  // tiếng Việt, hiển thị thẳng cho user
  },
  "timestamp": "2026-09-05T00:39:15.148Z"  // ISO-8601 UTC, giờ server xử lý xong
}
```

### 1.2. Naming convention — JSON dùng snake_case

Toàn bộ request/response body dùng **snake_case**, kể cả khi entity Java là camelCase
(`display_name` ↔ Java field `displayName`). Áp dụng toàn cục qua
`spring.jackson.property-naming-strategy: SNAKE_CASE`.

### 1.3. Content-Type

Mọi request có body: `Content-Type: application/json`. Server bỏ qua field lạ trong JSON gửi lên
(`fail-on-unknown-properties: false`) — gửi thừa field không bị 400.

### 1.4. Format thời gian

`Instant` Java → chuỗi ISO-8601 UTC, ví dụ `2026-09-05T00:39:15.148Z`. Không dùng epoch millis.

### 1.5. Rate limit / khóa tạm thời

Hai cơ chế khóa độc lập theo **email**, TTL 15 phút, lưu ở Redis:

| Cơ chế | Ngưỡng | Áp dụng cho |
|---|---|---|
| Chống brute-force đăng nhập | 5 lần sai liên tiếp | `POST /login` |
| Chống dò OTP | 5 lần sai liên tiếp | `POST /verify-email` (sai quá 5 lần → OTP bị hủy, phải đăng ký lại luồng lấy mã) |

---

## 2. XÁC THỰC (AUTHENTICATION)

7/8 endpoint auth là **public** (không cần token) — hợp lý vì đây chính là nơi *tạo ra* token.
Duy nhất **`PUT /change-password`** yêu cầu đăng nhập.

```
Authorization: Bearer <access_token>
```

- **Access token**: JWT, ký HMAC-SHA256, sống **15 phút**. Gửi ở header `Authorization` cho MỌI
  endpoint cần đăng nhập (không riêng auth — toàn bộ API từ Phase 2 trở đi cũng theo cách này).
- **Refresh token**: chuỗi ngẫu nhiên (không phải JWT), sống **7 ngày**, dùng đúng 1 lần qua
  endpoint `/refresh` để đổi lấy cặp token mới (xem [§3.4](#34-post-apiv1authrefresh)).

Thiếu header hoặc token sai/hết hạn ở endpoint cần đăng nhập → **401 `UNAUTHORIZED`**.
Có token hợp lệ nhưng thiếu quyền (role) → **403 `ACCESS_DENIED`** (chưa endpoint nào trong Phase 1
dùng nhánh này — dành cho Phase sau với `@PreAuthorize`).

---

## 3.1. `POST /api/v1/auth/register`

Đăng ký tài khoản mới. Tạo user ở trạng thái `PENDING_VERIFICATION`, gửi mã OTP 6 chữ số qua email
(MailHog ở dev: http://localhost:8025). **Không** trả token — phải xác thực email rồi mới đăng nhập được.

**Auth**: không cần · **Thành công**: `201 Created`

### Request body

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `email` | string | ✅ | `@Email`, tối đa 255 ký tự |
| `password` | string | ✅ | 8–72 ký tự, **≥1 chữ hoa và ≥1 chữ số** (xem ghi chú) |
| `username` | string | ✅ | 3–50 ký tự, chỉ `[a-zA-Z0-9_]` |
| `display_name` | string | ✅ | tối đa 100 ký tự |

> **Vì sao mật khẩu tối đa 72 ký tự**: BCrypt chỉ băm 72 byte đầu tiên, phần dư bị cắt **âm thầm**
> (không báo lỗi). Giới hạn này chặn ngộ nhận "mật khẩu càng dài càng an toàn" ngay ở input.

```json
{
  "email": "alice@example.com",
  "password": "Password1",
  "username": "alice_w",
  "display_name": "Alice Wonderland"
}
```

### Response 201

```json
{
  "success": true,
  "data": null,
  "error": null,
  "timestamp": "2026-09-05T02:10:00.000Z"
}
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Sai định dạng bất kỳ field nào ở bảng trên |
| 409 | `EMAIL_ALREADY_EXISTS` | Email đã có tài khoản (so sánh sau khi chuẩn hóa `trim + lowercase`) |
| 409 | `USERNAME_ALREADY_EXISTS` | Username đã bị chiếm |

### Ghi chú vận hành

- Email được **chuẩn hóa**: `"  Alice@Example.COM "` → `"alice@example.com"` trước khi lưu và so
  sánh. `Alice@x.com` và `alice@x.com` là **cùng một tài khoản** — đăng ký lần 2 sẽ bị 409.
- `username` **phân biệt hoa/thường** (nợ kỹ thuật đã ghi nhận — `Admin` và `admin` là 2 user khác nhau).
- Việc gửi email chạy **nền**, sau khi transaction đăng ký commit xong. Response 201 trả về **trước
  khi** mail chắc chắn đã gửi — đừng coi 201 là bằng chứng mail đã tới hộp thư.
- Nếu SMTP chết, đăng ký vẫn 201 nhưng mail sẽ không tới — hiện chưa có endpoint "gửi lại OTP"
  (nợ kỹ thuật #1 trong báo cáo Phase 1).

### curl

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Password1","username":"alice_w","display_name":"Alice Wonderland"}'
```

---

## 3.2. `POST /api/v1/auth/verify-email`

Xác thực email bằng mã OTP nhận được sau khi đăng ký. Chuyển user từ `PENDING_VERIFICATION` sang
`ACTIVE`. Sau bước này mới đăng nhập được.

**Auth**: không cần · **Thành công**: `200 OK`

### Request body

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `email` | string | ✅ | `@Email` |
| `otp` | string | ✅ | đúng 6 chữ số (`\d{6}`) |

```json
{ "email": "alice@example.com", "otp": "483920" }
```

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "2026-09-05T02:12:30.000Z" }
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `otp` không đúng 6 chữ số |
| 400 | `INVALID_VERIFICATION_TOKEN` | OTP sai, đã hết hạn (15'), hoặc đã bị dùng/hủy |
| 404 | `USER_NOT_FOUND` | OTP đúng nhưng không tìm thấy user theo email (dữ liệu bất thường, hiếm gặp) |

### Ghi chú vận hành

- OTP có **TTL 15 phút** tính từ lúc `register()` sinh ra.
- OTP có **giới hạn 5 lần thử sai** — sai lần thứ 5 thì mã bị **hủy ngay lập tức**, những lần thử
  tiếp theo (kể cả đúng) đều nhận `INVALID_VERIFICATION_TOKEN`. Phải đăng ký lại (hoặc chờ tính năng
  gửi lại OTP ở Phase sau) để lấy mã mới.
- Gọi lại endpoint này sau khi đã `ACTIVE` rồi: OTP cũ đã bị xóa lúc xác thực thành công lần đầu →
  nhận `INVALID_VERIFICATION_TOKEN`, không có tác dụng phụ gì khác (không lỗi 500, không đổi trạng thái).

### curl

```bash
curl -X POST http://localhost:8080/api/v1/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","otp":"483920"}'
```

---

## 3.3. `POST /api/v1/auth/login`

Đăng nhập bằng email + mật khẩu, nhận cặp access token + refresh token.

**Auth**: không cần · **Thành công**: `200 OK`

### Request body

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `email` | string | ✅ | `@Email` |
| `password` | string | ✅ | không giới hạn định dạng — chỉ cần khác rỗng |

```json
{ "email": "alice@example.com", "password": "Password1" }
```

> Login **không** áp `@StrongPassword` như register — luật mật khẩu có thể siết dần theo thời gian,
> user cũ đăng ký trước khi luật đổi vẫn phải đăng nhập được.

### Response 200

```json
{
  "success": true,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI4ZjE0ZTQ1Ny0uLi4iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc1...",
    "refresh_token": "K3xQmZ9vN2wR7pL4tY8sB1uJ6hD0eF-cA5gI3oX",
    "expires_in": 900
  },
  "error": null,
  "timestamp": "2026-09-05T02:15:00.000Z"
}
```

| Field trong `data` | Kiểu | Ý nghĩa |
|---|---|---|
| `access_token` | string (JWT) | Gửi ở header `Authorization: Bearer ...` cho mọi request cần đăng nhập |
| `refresh_token` | string (opaque, KHÔNG phải JWT) | Dùng đúng 1 lần ở `/refresh` để lấy cặp mới; lưu an toàn (localStorage rủi ro XSS, cookie HttpOnly an toàn hơn — quyết định của FE) |
| `expires_in` | number (giây) | Tuổi thọ `access_token`, mặc định `900` = 15 phút |

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Email sai định dạng / password rỗng |
| 401 | `INVALID_CREDENTIALS` | Email không tồn tại **hoặc** sai mật khẩu — **cố tình trả cùng 1 lỗi** để chống dò email (user enumeration) |
| 403 | `EMAIL_NOT_VERIFIED` | Đúng mật khẩu nhưng chưa xác thực OTP (`status = PENDING_VERIFICATION`) |
| 403 | `ACCOUNT_LOCKED` | Đúng mật khẩu nhưng tài khoản `LOCKED` (admin khóa) hoặc `DEACTIVATED` (tự vô hiệu hóa) |
| 429 | `TOO_MANY_LOGIN_ATTEMPTS` | Sai mật khẩu ≥ 5 lần liên tiếp trong 15 phút gần nhất |

### Ghi chú vận hành

- **Thứ tự kiểm tra quan trọng**: khóa brute-force được xét **trước tiên** (không chạm DB nếu đã
  khóa) → rồi mới tới email/mật khẩu → rồi mới tới trạng thái tài khoản. Vì vậy nếu tài khoản bị
  khóa (`ACCOUNT_LOCKED`) mà đồng thời cũng đang bị rate-limit brute-force, bạn sẽ thấy
  `429 TOO_MANY_LOGIN_ATTEMPTS` trước, không phải `403 ACCOUNT_LOCKED`.
- Đăng nhập thành công sẽ **xóa bộ đếm sai** của email đó và cập nhật `last_login_at`.
- Mỗi lần đăng nhập tạo một **phiên (refresh token) mới**, không thu hồi các phiên khác đang hoạt
  động — cho phép đăng nhập nhiều thiết bị cùng lúc.
- `deviceInfo` lưu kèm theo phiên lấy từ header `User-Agent` (không bắt buộc, `null` cũng được).

### curl

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Password1"}'
```

---

## 3.4. `POST /api/v1/auth/refresh`

Đổi refresh token lấy cặp token mới. **Refresh token cũ bị vô hiệu hóa ngay lập tức** (rotation) —
đây không phải endpoint "gia hạn", mà là "đổi lấy bộ mới, hủy bộ cũ".

**Auth**: không cần header Bearer — chính refresh token trong body là bằng chứng danh tính
**Thành công**: `200 OK`

### Request body

| Field | Kiểu | Bắt buộc |
|---|---|---|
| `refresh_token` | string | ✅ |

```json
{ "refresh_token": "K3xQmZ9vN2wR7pL4tY8sB1uJ6hD0eF-cA5gI3oX" }
```

### Response 200

Cấu trúc **giống hệt** response của `/login`:

```json
{
  "success": true,
  "data": {
    "access_token": "eyJ...(mới)",
    "refresh_token": "8nQ2...(mới, KHÁC token vừa gửi lên)",
    "expires_in": 900
  },
  "error": null,
  "timestamp": "2026-09-05T02:30:00.000Z"
}
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `refresh_token` rỗng |
| 401 | `INVALID_REFRESH_TOKEN` | Token không tồn tại, **hoặc đã bị thu hồi** (xem cảnh báo dưới) |
| 401 | `REFRESH_TOKEN_EXPIRED` | Token còn hợp lệ về mặt thu hồi nhưng đã quá 7 ngày |

### ⚠️ Cảnh báo quan trọng — token reuse detection

Gọi `/refresh` **hai lần với cùng một refresh token** là dấu hiệu bị đánh cắp (client thật đã đổi
token rồi, ai đó khác lại trình ra bản cũ). Hệ thống phản ứng: **thu hồi toàn bộ phiên đăng nhập
của user đó** (mọi thiết bị), rồi mới trả `401 INVALID_REFRESH_TOKEN`.

```
Client A: refresh(token_1) → 200, nhận token_2 (token_1 giờ đã revoked)
Client B (kẻ trộm, có bản sao token_1):
  refresh(token_1) → 401 INVALID_REFRESH_TOKEN
                      + TOÀN BỘ phiên của user (kể cả token_2 của Client A!) bị thu hồi
Client A: refresh(token_2) → cũng nhận 401, phải đăng nhập lại
```

**Hệ quả cho FE**: nếu có race condition khiến FE gọi `/refresh` 2 lần với cùng token cũ (ví dụ 2 tab
cùng refresh một lúc), **cả phiên hợp lệ cũng bị đá theo**. FE nên khóa (mutex/lock) việc gọi
`/refresh` — chỉ 1 request refresh tại một thời điểm cho mỗi token.

### curl

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token":"K3xQmZ9vN2wR7pL4tY8sB1uJ6hD0eF-cA5gI3oX"}'
```

---

## 3.5. `POST /api/v1/auth/logout`

Thu hồi refresh token của **thiết bị hiện tại** (không đá các thiết bị khác).

**Auth**: không cần header Bearer (chỉ cần trình ra refresh token) · **Thành công**: `200 OK`

### Request body

| Field | Kiểu | Bắt buộc |
|---|---|---|
| `refresh_token` | string | ✅ |

```json
{ "refresh_token": "K3xQmZ9vN2wR7pL4tY8sB1uJ6hD0eF-cA5gI3oX" }
```

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "2026-09-05T02:40:00.000Z" }
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `refresh_token` rỗng |

**Không có lỗi 401/404 cho token không tồn tại/đã revoke** — endpoint này **idempotent theo chủ ý**:
gọi logout với token rác, token đã hết hạn, hay gọi logout 2 lần liên tiếp đều trả `200` như nhau.
Về mặt hiệu ứng, "đăng xuất" đã đạt được (không còn token nào hợp lệ để dùng) bất kể trạng thái ban đầu.

### Ghi chú vận hành

- `access_token` (JWT) **không** bị vô hiệu hóa bởi logout — nó vẫn hợp lệ đến khi hết hạn (tối đa
  15 phút sau). Đây là đánh đổi vốn có của JWT tự xác thực (không tra DB được → không thu hồi được).
  Cửa sổ rủi ro tối đa là 15 phút.
- Access token cũ vẫn gọi được API khác cho tới khi hết hạn tự nhiên, kể cả sau khi đã logout.

### curl

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refresh_token":"K3xQmZ9vN2wR7pL4tY8sB1uJ6hD0eF-cA5gI3oX"}'
```

---

## 3.6. `POST /api/v1/auth/forgot-password`

Gửi email chứa link đặt lại mật khẩu.

**Auth**: không cần · **Thành công**: `200 OK` **LUÔN LUÔN**, kể cả khi email không tồn tại.

### Request body

| Field | Kiểu | Bắt buộc |
|---|---|---|
| `email` | string | ✅ `@Email` |

```json
{ "email": "alice@example.com" }
```

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "2026-09-05T02:45:00.000Z" }
```

### ⚠️ Hành vi cố ý khác thường

Endpoint này **không bao giờ** trả lỗi kiểu "email không tồn tại". Dù email có trong hệ thống hay
không, response luôn là `200` giống hệt nhau. Đây là đánh đổi bảo mật: nếu báo lỗi khi email không
tồn tại, endpoint công khai này sẽ trở thành công cụ để dò xem địa chỉ nào đã đăng ký ChatSphere.

**Cách kiểm tra email có tồn tại hay không (khi test)**: xem MailHog có mail mới hay không — có mail
nghĩa là email đó có tài khoản; không có mail sau vài giây nghĩa là không có (nhưng response HTTP
thì như nhau).

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Email sai định dạng |

### Ghi chú vận hành

- Link trong mail dạng: `{app.frontend-url}/reset-password?token={uuid}` — dev mặc định
  `http://localhost:5173/reset-password?token=...`.
- Token reset có **TTL 15 phút**, dùng **đúng 1 lần** (đọc xong trong `/reset-password` là bị xóa
  ngay lập tức, kể cả reset thất bại vì lý do khác).
- Chưa có giới hạn tần suất gọi endpoint này theo email (nợ kỹ thuật #3) — về lý thuyết có thể bị
  lợi dụng để spam mail cho một địa chỉ.

### curl

```bash
curl -X POST http://localhost:8080/api/v1/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com"}'
```

---

## 3.7. `POST /api/v1/auth/reset-password`

Đặt mật khẩu mới bằng token nhận được từ email `forgot-password`.

**Auth**: không cần · **Thành công**: `200 OK`

### Request body

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `token` | string | ✅ | UUID lấy từ link trong mail |
| `new_password` | string | ✅ | cùng luật với `password` ở register: 8–72 ký tự, ≥1 hoa, ≥1 số |

```json
{
  "token": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "new_password": "NewPassword9"
}
```

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "2026-09-05T02:50:00.000Z" }
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `new_password` không đạt luật mật khẩu mạnh |
| 400 | `INVALID_RESET_TOKEN` | Token sai, đã hết hạn (15'), hoặc **đã được dùng rồi** |
| 404 | `USER_NOT_FOUND` | Token hợp lệ nhưng user đã bị xóa (hiếm gặp) |

### Ghi chú vận hành

- Đặt lại mật khẩu thành công sẽ **thu hồi TOÀN BỘ refresh token** của user — mọi thiết bị đang
  đăng nhập đều bị đá ra, phải đăng nhập lại bằng mật khẩu mới.
- Gọi lại endpoint với **cùng token** lần thứ 2 (dù lần đầu thành công hay thất bại vì lý do khác)
  sẽ nhận `INVALID_RESET_TOKEN` — token bị xóa khỏi Redis ngay khi đọc, không phụ thuộc kết quả bước
  sau đó.
- Endpoint **không kiểm tra** `user.status` — kể cả tài khoản `LOCKED` vẫn đặt lại mật khẩu được
  (dù sau đó vẫn không đăng nhập được vì đang khóa). Nợ kỹ thuật #6.

### curl

```bash
curl -X POST http://localhost:8080/api/v1/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"token":"7c9e6679-7425-40de-944b-e07fc1f90ae7","new_password":"NewPassword9"}'
```

---

## 3.8. `PUT /api/v1/auth/change-password`

Đổi mật khẩu khi **đã đăng nhập**. Endpoint **duy nhất** trong module Auth yêu cầu `Authorization` header.

**Auth**: ✅ **Bearer access_token bắt buộc** · **Thành công**: `200 OK`

### Headers

```
Authorization: Bearer <access_token>
Content-Type: application/json
```

### Request body

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `old_password` | string | ✅ | không giới hạn định dạng, chỉ cần khớp mật khẩu hiện tại |
| `new_password` | string | ✅ | 8–72 ký tự, ≥1 hoa, ≥1 số, **và phải khác `old_password`** |

```json
{ "old_password": "Password1", "new_password": "NewPassword9" }
```

### Response 200

```json
{ "success": true, "data": null, "error": null, "timestamp": "2026-09-05T02:55:00.000Z" }
```

### Lỗi có thể gặp

| HTTP | `error.code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `new_password` sai định dạng, **hoặc** trùng với `old_password` |
| 400 | `WRONG_OLD_PASSWORD` | `old_password` không khớp mật khẩu hiện tại |
| 401 | `UNAUTHORIZED` | Thiếu/sai/hết hạn `access_token` |

### Ghi chú vận hành

- **`userId` lấy từ token, không phải từ body** — không có field `user_id` trong request. Bạn chỉ
  có thể đổi mật khẩu của **chính tài khoản đang đăng nhập**.
- Đổi thành công sẽ **thu hồi toàn bộ refresh token** của user, giống `reset-password` — mọi thiết
  bị khác phải đăng nhập lại. `access_token` hiện tại (đang dùng để gọi chính request này) vẫn còn
  hiệu lực tới khi JWT hết hạn tự nhiên (không thu hồi được JWT).

### curl

```bash
curl -X PUT http://localhost:8080/api/v1/auth/change-password \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...." \
  -H "Content-Type: application/json" \
  -d '{"old_password":"Password1","new_password":"NewPassword9"}'
```

---

## 4. BẢNG TỔNG HỢP MÃ LỖI

| `error.code` | HTTP | Endpoint có thể gặp | Message mặc định (VI) |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | tất cả | Dữ liệu không hợp lệ (kèm chi tiết field trong `message`) |
| `EMAIL_ALREADY_EXISTS` | 409 | register | Email đã được sử dụng |
| `USERNAME_ALREADY_EXISTS` | 409 | register | Tên người dùng đã tồn tại |
| `USER_NOT_FOUND` | 404 | verify-email, reset-password | Không tìm thấy người dùng |
| `INVALID_CREDENTIALS` | 401 | login | Email hoặc mật khẩu không đúng |
| `EMAIL_NOT_VERIFIED` | 403 | login | Tài khoản chưa xác thực email |
| `ACCOUNT_LOCKED` | 403 | login | Tài khoản đang bị khóa |
| `INVALID_VERIFICATION_TOKEN` | 400 | verify-email | Mã xác thực không hợp lệ hoặc đã hết hạn |
| `INVALID_REFRESH_TOKEN` | 401 | refresh | Refresh token không hợp lệ |
| `REFRESH_TOKEN_EXPIRED` | 401 | refresh | Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại |
| `TOO_MANY_LOGIN_ATTEMPTS` | 429 | login | Đăng nhập sai quá nhiều lần, vui lòng thử lại sau 15 phút |
| `INVALID_RESET_TOKEN` | 400 | reset-password | Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn |
| `WRONG_OLD_PASSWORD` | 400 | change-password | Mật khẩu hiện tại không đúng |
| `UNAUTHORIZED` | 401 | change-password (thiếu token) | Chưa xác thực |
| `ACCESS_DENIED` | 403 | (dự phòng cho Phase sau) | Bạn không có quyền thực hiện thao tác này |
| `INTERNAL_ERROR` | 500 | bất kỳ (lưới cuối) | Đã có lỗi xảy ra, vui lòng thử lại sau |

---

## 5. KỊCH BẢN TEST END-TO-END

### 5.1. Luồng chính (happy path)

```
1. POST /register          → 201
2. (đọc OTP ở MailHog :8025)
3. POST /verify-email       → 200
4. POST /login               → 200, lưu access_token + refresh_token
5. PUT  /change-password    → 200 (kèm Authorization header)
   (refresh_token cũ giờ đã bị thu hồi vì đổi mật khẩu — bước 6 phải fail)
6. POST /refresh (dùng refresh_token bước 4) → 401 INVALID_REFRESH_TOKEN
7. POST /login (mật khẩu MỚI) → 200, lấy cặp token khác
```

### 5.2. Rotation + phát hiện đánh cắp

```
1. POST /login                       → nhận refresh_token_1
2. POST /refresh {refresh_token_1}   → 200, nhận refresh_token_2 (token_1 giờ revoked)
3. POST /refresh {refresh_token_1}   → 401 INVALID_REFRESH_TOKEN (dùng lại token cũ)
4. POST /refresh {refresh_token_2}   → CŨNG 401 (bị đá theo, vì bước 3 coi là dấu hiệu bị đánh cắp)
5. POST /login (lại)                 → 200, phải đăng nhập lại từ đầu
```

### 5.3. Brute-force login

```
Lặp 5 lần: POST /login {email, password: "sai"} → mỗi lần 401 INVALID_CREDENTIALS
Lần thứ 6: POST /login (kể cả mật khẩu ĐÚNG lần này) → 429 TOO_MANY_LOGIN_ATTEMPTS
Chờ 15 phút (hoặc test set TTL ngắn hơn) → mở khóa lại
```

### 5.4. Brute-force OTP

```
Đăng ký xong, có OTP thật là "123456"
Lặp 5 lần: POST /verify-email {otp: "000000"} → 400 INVALID_VERIFICATION_TOKEN (mỗi lần)
Lần thứ 6, dùng OTP ĐÚNG "123456" → VẪN 400 INVALID_VERIFICATION_TOKEN (mã đã bị đốt ở lần sai thứ 5)
```

### 5.5. Bảo mật change-password

```
1. PUT /change-password (KHÔNG có header Authorization) → 401 UNAUTHORIZED
2. PUT /change-password (Authorization: Bearer <token rác>) → 401 UNAUTHORIZED
3. PUT /change-password (token hợp lệ, old_password sai) → 400 WRONG_OLD_PASSWORD
4. PUT /change-password (token hợp lệ, new_password == old_password) → 400 VALIDATION_ERROR
```

### 5.6. User enumeration (không nên phân biệt được)

```
POST /login {email: "khong-ton-tai@x.com", password: "bat-ky"} → 401 INVALID_CREDENTIALS
POST /login {email: "ton-tai@x.com",       password: "sai"}    → 401 INVALID_CREDENTIALS
→ Hai response giống hệt nhau (status, code, message) — không cách nào phân biệt qua HTTP response.

POST /forgot-password {email: "khong-ton-tai@x.com"} → 200 (giống hệt email tồn tại)
```

---

## 6. POSTMAN / CURL COLLECTION

### 6.1. Biến môi trường gợi ý (Postman)

| Variable | Giá trị mẫu |
|---|---|
| `base_url` | `http://localhost:8080` |
| `access_token` | (set tự động từ response `/login`) |
| `refresh_token` | (set tự động từ response `/login`) |

Trong tab **Tests** của request `login`, thêm script để tự động lưu token cho các request sau:

```javascript
const body = pm.response.json();
if (body.success) {
    pm.environment.set("access_token", body.data.access_token);
    pm.environment.set("refresh_token", body.data.refresh_token);
}
```

### 6.2. Toàn bộ curl — copy chạy tuần tự

```bash
BASE=http://localhost:8080/api/v1/auth

# 1. Đăng ký
curl -s -X POST $BASE/register -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Password1","username":"alice_w","display_name":"Alice"}' | jq

# 2. Lấy OTP từ MailHog API (thay vì mở trình duyệt)
curl -s http://localhost:8025/api/v2/messages | jq -r '.items[0].Content.Body'

# 3. Xác thực (thay OTP thật vào)
curl -s -X POST $BASE/verify-email -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","otp":"123456"}' | jq

# 4. Đăng nhập, lưu token vào biến shell
RESP=$(curl -s -X POST $BASE/login -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Password1"}')
echo $RESP | jq
ACCESS=$(echo $RESP | jq -r '.data.access_token')
REFRESH=$(echo $RESP | jq -r '.data.refresh_token')

# 5. Gọi API cần đăng nhập
curl -s -X PUT $BASE/change-password \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d '{"old_password":"Password1","new_password":"NewPassword9"}' | jq

# 6. Refresh (sẽ fail vì bước 5 đã thu hồi refresh token cũ)
curl -s -X POST $BASE/refresh -H "Content-Type: application/json" \
  -d "{\"refresh_token\":\"$REFRESH\"}" | jq

# 7. Đăng nhập lại với mật khẩu mới
curl -s -X POST $BASE/login -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"NewPassword9"}' | jq
```

> Cần `jq` để format JSON đẹp (`choco install jq` trên Windows, hoặc bỏ `| jq` nếu không có).

---

## PHỤ LỤC — Sơ đồ trạng thái tài khoản liên quan tới đăng nhập

```
PENDING_VERIFICATION --[verify-email OTP đúng]--> ACTIVE
ACTIVE --[admin khóa, ngoài phạm vi Phase 1]--> LOCKED
ACTIVE --[user tự vô hiệu hóa, ngoài phạm vi Phase 1]--> DEACTIVATED

login() chỉ thành công khi status = ACTIVE:
  PENDING_VERIFICATION → 403 EMAIL_NOT_VERIFIED
  LOCKED / DEACTIVATED → 403 ACCOUNT_LOCKED
```

Xem `06_PHASE1_AUTH_REPORT.md` §6–§9 để hiểu **vì sao** thiết kế theo cách này (lý thuyết JWT vs
opaque token, chống user enumeration, transaction, v.v.).
