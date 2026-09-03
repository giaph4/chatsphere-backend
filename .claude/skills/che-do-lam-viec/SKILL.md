---
name: che-do-lam-viec
description: >
  Phân biệt 2 chế độ làm việc trên dự án ChatSphere. Kích hoạt Explain Mode
  khi người dùng nói "giải thích cho tôi", "cho tôi xem code", "code mẫu",
  "tôi muốn tự gõ theo", "đừng sửa file, chỉ in ra" — Claude CHỈ in code trong
  chat, KHÔNG dùng Write/Edit lên backend/src hay frontend/src. Kích hoạt Direct
  Mode khi người dùng nói "code thẳng vào src", "tạo file giúp tôi", "implement
  Phase X", "áp dụng vào project" — Claude tạo/sửa file thật. Không rõ thì hỏi
  lại đúng 1 câu.
---

# Chế độ làm việc: Explain Mode vs Direct Mode

Đặc tả đầy đủ: `05_CLAUDE_CODE_SKILL.md`. Tài liệu tham chiếu:
`01_SYSTEM_DESIGN.md`, `02_SETUP_GUIDE.md`, `03_CODE_ROADMAP.md`.

## Chọn chế độ

**Explain Mode** khi người dùng dùng cụm từ:
- "giải thích cho tôi…", "cho tôi xem code…", "code mẫu…", "hướng dẫn tôi code…"
- "tôi muốn tự code theo", "đừng sửa file, chỉ in ra thôi", "show code ở đây thôi"
- Ngữ cảnh là buổi *học* / *luyện tập* một khái niệm cụ thể
- **Mặc định** khi ý định chưa rõ và đang ở giai đoạn đầu tìm hiểu 1 tính năng mới (ưu tiên an toàn — không đụng vào `src`)

**Direct Mode** khi người dùng dùng cụm từ:
- "code thẳng vào src", "code luôn giúp tôi", "tạo file giúp tôi", "implement Phase X giúp tôi"
- "sửa trực tiếp file…", "áp dụng vào project luôn"
- Đang follow `03_CODE_ROADMAP.md` và yêu cầu triển khai 1 checklist item cụ thể

**Không chắc chắn** → hỏi lại đúng 1 câu duy nhất:
*"Bạn muốn mình in code ra đây để bạn tự gõ theo, hay code thẳng vào file trong project?"*

## Explain Mode — hành vi

- Trình bày code trong markdown code block ngay trong câu trả lời. **KHÔNG dùng
  Write/Edit lên bất kỳ file nào trong `backend/src` hoặc `frontend/src`.**
- Luôn giải thích **tại sao** code viết như vậy — bắt buộc với: JWT/Security
  filter chain, WebSocket/STOMP interceptor, WebRTC signaling relay, ICE
  candidate queue, thuật toán TURN credential HMAC.
- Ghi **đường dẫn file dự kiến** ở đầu mỗi code block
  (vd `// File: backend/src/main/java/com/chatsphere/auth/JwtTokenProvider.java`)
  nhưng KHÔNG tạo file đó.
- Được phép tạo file **ngoài `src`** cho mục đích minh họa/ghi chú (`docs/`, scratchpad).
- Kết thúc bằng gợi ý bước tiếp theo trong `03_CODE_ROADMAP.md`.
- Nếu người dùng nói "ok giờ code thẳng vào giúp tôi" → chuyển Direct Mode ngay
  cho đúng phần vừa giải thích, không bắt lặp lại yêu cầu.

## Direct Mode — hành vi

- Dùng Write/Edit tạo/sửa file thật theo package structure `com.chatsphere.*`
  (backend) và cấu trúc `src/` (frontend) đã định nghĩa ở `02_SETUP_GUIDE.md` mục 2.
- Trước khi code, xác định đang ở **Phase nào** trong `03_CODE_ROADMAP.md` để
  đảm bảo thứ tự phụ thuộc (không code Phase 4 WebSocket khi Phase 1 Auth chưa xong).
- Sau khi tạo/sửa file, đối chiếu lại checklist item tương ứng và báo đã xong mục nào.
- Tuân thủ `01_SYSTEM_DESIGN.md`: tên bảng/cột (mục 7), format API response
  (mục 8.1), package structure (mục 3.3). Không tự ý đổi thiết kế đã chốt — nếu
  phát hiện bất hợp lý, dừng lại trao đổi trước.
- Không code vượt quá phạm vi 1 lượt yêu cầu (được yêu cầu `AuthService.login()`
  thì không tự tiện code luôn `AuthController` trừ khi không thể tách rời để chạy).
- Sau khi xong 1 đơn vị công việc, đề xuất chạy test liên quan hoặc lệnh `curl`
  kiểm tra nhanh.

## Phong cách code (cả 2 chế độ)

- Clean code: tên rõ nghĩa, method ngắn, Single Responsibility.
- Comment ở đoạn logic phức tạp (ưu tiên tiếng Việt cho nghiệp vụ, tiếng Anh
  cho thuật ngữ kỹ thuật) — bắt buộc ở các điểm liệt kê phần Explain Mode.
- Không bao giờ `catch (Exception e) {}` rỗng; dùng `BusinessException` +
  `ErrorCode` (Phase 0); validate input ở tầng DTO bằng Bean Validation.
- Khi chọn thư viện/pattern, nêu ngắn gọn ưu/nhược so với ít nhất 1 phương án khác.
- Hạn chế câu xã giao mở đầu/kết thúc — đi thẳng vào nội dung kỹ thuật.
