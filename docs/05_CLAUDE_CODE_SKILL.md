# TÀI LIỆU SKILL CHO CLAUDE CODE
## ChatSphere — Chế độ làm việc: "Giải thích" (Explain Mode) vs "Code thẳng" (Direct Mode)

**Tài liệu liên quan:** `01_SYSTEM_DESIGN.md`, `02_SETUP_GUIDE.md`, `03_CODE_ROADMAP.md`, `04_PRODUCTION_DEPLOYMENT.md`

---

## 1. VẤN ĐỀ CẦN GIẢI QUYẾT

Khi làm việc với Claude Code trong dự án ChatSphere, có 2 nhu cầu khác nhau tùy thời điểm học tập:

1. **Chế độ Giải thích (Explain Mode)**: người dùng muốn **tự tay gõ code** để luyện kỹ năng — Claude chỉ nên hiển thị code mẫu ngay trong cuộc trò chuyện (chat), giải thích logic, rồi để người dùng tự gõ lại vào IDE của họ. Claude **không** được tự ý tạo/sửa file trong thư mục `src`.
2. **Chế độ Code thẳng (Direct Mode)**: người dùng đã hiểu rõ phần lý thuyết, muốn tiết kiệm thời gian — yêu cầu Claude **tạo/sửa file trực tiếp** vào đúng vị trí trong cấu trúc dự án (`backend/src/...`, `frontend/src/...`).

Không có cơ chế rõ ràng để phân biệt 2 chế độ này thì Claude có thể code thẳng vào `src` ngay cả khi người dùng chỉ muốn xem để tự luyện tay, hoặc ngược lại chỉ in code ra chat khi người dùng đang muốn đẩy nhanh tiến độ.

## 2. QUY TẮC KÍCH HOẠT CHẾ ĐỘ

### 2.1. Explain Mode được kích hoạt khi người dùng dùng các cụm từ như:

- "giải thích cho tôi...", "cho tôi xem code...", "code mẫu...", "hướng dẫn tôi code..."
- "tôi muốn tự code theo", "đừng sửa file, chỉ in ra thôi", "show code ở đây thôi"
- Khi ngữ cảnh cho thấy đây là buổi *học*/*luyện tập* một khái niệm cụ thể (ví dụ "giải thích cách JWT filter hoạt động và code mẫu")
- **Mặc định** khi không rõ ràng và đang ở giai đoạn đầu tìm hiểu 1 tính năng mới (ưu tiên an toàn — không tự ý đụng vào `src` khi chưa chắc ý định).

### 2.2. Direct Mode được kích hoạt khi người dùng dùng các cụm từ như:

- "code thẳng vào src", "code luôn giúp tôi", "tạo file giúp tôi", "implement Phase X giúp tôi"
- "sửa trực tiếp file...", "áp dụng vào project luôn"
- Khi người dùng đang follow theo `03_CODE_ROADMAP.md` và yêu cầu triển khai 1 checklist item cụ thể ("làm Phase 1 mục 1.2 giúp tôi", "code AuthService đi")

### 2.3. Khi không chắc chắn

Hỏi lại ngắn gọn 1 câu duy nhất: *"Bạn muốn mình in code ra đây để bạn tự gõ theo, hay code thẳng vào file trong project?"* — không tự suy đoán khi tín hiệu không rõ ràng, vì hành vi 2 chế độ khác biệt hoàn toàn (ảnh hưởng trực tiếp tới file thật trong dự án).

## 3. HÀNH VI CHI TIẾT TỪNG CHẾ ĐỘ

### 3.1. Explain Mode — quy tắc hành vi

- Trình bày code trong markdown code block ngay trong câu trả lời chat, **không dùng Write/Edit tool lên bất kỳ file nào trong `backend/src` hoặc `frontend/src`**.
- Luôn giải thích **tại sao** code viết như vậy (đặc biệt các đoạn logic phức tạp: JWT filter, WebSocket interceptor, WebRTC ICE candidate queue...), không chỉ đưa code suông.
- Nếu code liên quan đến nhiều file, trình bày rõ **đường dẫn file dự kiến** (ví dụ `// File: backend/src/main/java/com/chatsphere/auth/JwtTokenProvider.java`) ở đầu mỗi code block để người dùng biết đặt vào đâu, nhưng KHÔNG tự tạo file đó.
- Có thể tạo file **ngoài `src`** nếu cần thiết cho mục đích minh họa/tài liệu (ví dụ file ghi chú tạm trong `docs/` hoặc `scratchpad`) — ranh giới cấm chỉ áp dụng cho thư mục source code chính.
- Kết thúc phần giải thích bằng gợi ý bước tiếp theo trong `03_CODE_ROADMAP.md` (ví dụ: "Sau khi gõ xong đoạn này, bước tiếp theo trong checklist Phase 1 là...").
- Nếu người dùng sau đó nói "ok giờ code thẳng vào giúp tôi" → chuyển sang Direct Mode ngay cho đúng phần vừa giải thích, không bắt người dùng lặp lại yêu cầu từ đầu.

### 3.2. Direct Mode — quy tắc hành vi

- Dùng Write/Edit tool để tạo/sửa file thật trong đúng cấu trúc thư mục đã định nghĩa ở `02_SETUP_GUIDE.md` mục 2 (package structure `com.chatsphere.*` cho backend, cấu trúc `src/` cho frontend).
- Trước khi code, xác định rõ đang ở **Phase nào** trong `03_CODE_ROADMAP.md` để đảm bảo thứ tự phụ thuộc đúng (không code Phase 4 WebSocket khi Phase 1 Auth chưa xong, vì Phase 4 cần `JwtTokenProvider` đã có).
- Sau khi tạo/sửa file, đối chiếu lại đúng checklist item tương ứng trong `03_CODE_ROADMAP.md` và báo cho người dùng biết đã hoàn thành mục nào.
- Tuân thủ nghiêm ngặt các quy ước đã thống nhất trong `01_SYSTEM_DESIGN.md`: tên bảng/cột (mục 7), format API response (mục 8.1), package structure (mục 3.3).
- Viết code sạch, có comment ở đoạn logic phức tạp, xử lý ngoại lệ đầy đủ (theo phong cách code đã thống nhất — xem mục 4 bên dưới).
- Sau khi code xong 1 đơn vị công việc, chủ động đề xuất chạy test liên quan (nếu có) hoặc hướng dẫn cách người dùng tự kiểm tra nhanh (ví dụ lệnh `curl` thử endpoint vừa tạo).
- Không tự ý code vượt quá phạm vi được yêu cầu trong 1 lượt (ví dụ được yêu cầu code `AuthService.login()` thì không tự tiện code luôn cả `AuthController` nếu không được yêu cầu — trừ khi rõ ràng 2 phần này không thể tách rời để chạy được).

## 4. PHONG CÁCH CODE ÁP DỤNG CHO CẢ 2 CHẾ ĐỘ

- Code sạch (clean code): tên biến/hàm rõ nghĩa, method ngắn gọn, tuân thủ nguyên tắc Single Responsibility.
- Comment tiếng Việt hoặc tiếng Anh (ưu tiên tiếng Việt cho phần giải thích nghiệp vụ, tiếng Anh cho thuật ngữ kỹ thuật chuẩn) ở những đoạn logic phức tạp — đặc biệt bắt buộc ở: JWT/Security filter chain, WebSocket/STOMP interceptor, WebRTC signaling relay, ICE candidate queue, thuật toán TURN credential HMAC.
- Xử lý ngoại lệ cẩn thận: không bao giờ để `catch (Exception e) {}` rỗng; luôn dùng `BusinessException` + `ErrorCode` đã định nghĩa ở Phase 0; validate input ở tầng DTO bằng Bean Validation trước khi vào Service.
- Khi đưa ra kiến trúc hoặc lựa chọn kỹ thuật (ví dụ chọn thư viện, pattern), luôn nêu ngắn gọn ưu/nhược điểm so với ít nhất 1 phương án thay thế — theo đúng phong cách đã dùng xuyên suốt ở `01_SYSTEM_DESIGN.md` mục 2.3.1.
- Hạn chế câu mở đầu/kết thúc xã giao trong phản hồi — đi thẳng vào nội dung kỹ thuật.

## 5. VÍ DỤ MINH HỌA 2 CHẾ ĐỘ TRÊN CÙNG 1 YÊU CẦU

**Yêu cầu người dùng**: "Giải thích cho tôi cách viết JwtTokenProvider"

→ **Explain Mode được kích hoạt** (do có từ "giải thích cho tôi"): Claude in code `JwtTokenProvider.java` đầy đủ trong code block kèm giải thích từng method (`generateAccessToken`, `validateToken`, `extractUserId`...), ghi chú đường dẫn file dự kiến, **không đụng vào file thật**.

**Yêu cầu người dùng**: "Code thẳng JwtTokenProvider vào project giúp tôi"

→ **Direct Mode được kích hoạt**: Claude dùng Write tool tạo trực tiếp `backend/src/main/java/com/chatsphere/auth/JwtTokenProvider.java`, báo đã hoàn thành mục nào trong checklist Phase 1.

## 6. GHI CHÚ TÍCH HỢP VỚI CÁC FILE TÀI LIỆU KHÁC

Khi làm việc ở cả 2 chế độ, Claude luôn tham chiếu ngược lại:
- `01_SYSTEM_DESIGN.md` để đảm bảo đúng thiết kế DB/API/kiến trúc đã thống nhất.
- `03_CODE_ROADMAP.md` để biết đang ở Phase nào, đảm bảo thứ tự phụ thuộc.
- Không tự ý thay đổi thiết kế đã chốt trong `01_SYSTEM_DESIGN.md` khi code — nếu phát hiện bất hợp lý trong lúc code, dừng lại và trao đổi với người dùng trước, cập nhật tài liệu thiết kế nếu cần thay vì âm thầm code khác đi.

---

*Đây là tài liệu tham khảo mô tả nội dung skill. Skill thực tế đã được đề xuất qua thẻ đề xuất (proposal card) để bạn lưu lại — sau khi lưu, chỉ cần nói "giải thích cho tôi..." hoặc "code thẳng vào giúp tôi..." trong các cuộc trò chuyện sau, Claude Code sẽ tự nhận diện đúng chế độ theo quy tắc ở tài liệu này.*
