# TÀI LIỆU SETUP HỆ THỐNG
## ChatSphere — Hướng dẫn cài đặt môi trường phát triển đầy đủ

**Tài liệu liên quan:** `01_SYSTEM_DESIGN.md` (kiến trúc & thiết kế), `03_CODE_ROADMAP.md` (lộ trình code sau khi setup xong)

---

> ## TRẠNG THÁI SETUP (cập nhật 2026-09-04)
>
> Phần **backend** đã được setup sẵn trong repo này. Tài liệu dưới đây mô tả trạng thái thực tế
> (một số chỗ khác bản kế hoạch ban đầu — đã ghi chú "⚠️ thực tế").
>
> | Hạng mục | Trạng thái |
> |---|---|
> | Spring Boot project | ✅ đã tạo — Spring Boot **4.1.1**, Java 21, package gốc `com.chatsphere` |
> | Dependency bổ sung (JWT, MapStruct, Lombok, MinIO, springdoc, web-push, Testcontainers) | ✅ đã thêm vào `pom.xml` |
> | Package structure (`auth`, `user`, `chat`, `signaling`, `presence`, `notification`, `media`, `common`, `config`) | ✅ đã tạo (mỗi package có `package-info.java`) |
> | `application.yaml` / `application-dev.yaml` / `application-prod.yaml` | ✅ đã có (đuôi `.yaml`, không phải `.yml`) |
> | `.env.example` + `.env` (gốc repo, `.env` đã gitignore) | ✅ đã có |
> | `src/main/resources/db/migration/` | ✅ thư mục đã tạo (script V1.. viết ở Phase 1) |
> | `infra/docker-compose.yml` + `infra/coturn/turnserver.conf` | ✅ đã có, đã chạy thử `docker compose up -d` OK |
> | Frontend | ❌ chưa khởi tạo (mục 7 — làm sau) |
> | Test Testcontainers (`mvnw test`) | ❌ chưa — thuộc `03_CODE_ROADMAP.md` Phase 0.8 / 0.10 |
>
> Đã verify: `./mvnw clean compile` OK; `./mvnw spring-boot:run` (profile `dev`, hạ tầng Docker chạy)
> → `Started ChatsphereBackendApplication`, `GET /actuator/health` = `{"status":"UP"}`.

---

## MỤC LỤC

1. [Yêu cầu môi trường](#1-yêu-cầu-môi-trường)
2. [Cấu trúc thư mục dự án](#2-cấu-trúc-thư-mục-dự-án)
3. [Setup hạ tầng bằng Docker Compose](#3-setup-hạ-tầng-bằng-docker-compose)
4. [Khởi tạo Backend Spring Boot](#4-khởi-tạo-backend-spring-boot)
5. [Cấu hình Backend chi tiết](#5-cấu-hình-backend-chi-tiết)
6. [Setup Database & Flyway Migration](#6-setup-database--flyway-migration)
7. [Khởi tạo Frontend React](#7-khởi-tạo-frontend-react)
8. [Cấu hình coturn (TURN server) cho local](#8-cấu-hình-coturn-turn-server-cho-local)
9. [Chạy thử toàn bộ hệ thống](#9-chạy-thử-toàn-bộ-hệ-thống)
10. [Kiểm tra (Verification checklist)](#10-kiểm-tra-verification-checklist)
11. [Xử lý sự cố thường gặp (Troubleshooting)](#11-xử-lý-sự-cố-thường-gặp-troubleshooting)

---

## 1. YÊU CẦU MÔI TRƯỜNG

### 1.1. Phần mềm bắt buộc

| Phần mềm | Phiên bản tối thiểu | Kiểm tra bằng lệnh |
|---|---|---|
| JDK | 21+ (đã test với JDK 25) | `java -version` |
| Maven | 3.9+ — hoặc dùng wrapper `./mvnw` có sẵn trong repo | `./mvnw -v` |
| Node.js | 20 LTS *(chỉ cần khi làm frontend — mục 7)* | `node -v` |
| npm | 10+ *(chỉ cần khi làm frontend)* | `npm -v` |
| Docker | 24+ (đã test với 29.x) | `docker -v` |
| Docker Compose | v2 (plugin) | `docker compose version` |
| Git | 2.x | `git --version` |

> ⚠️ Thực tế: repo đã kèm **Maven Wrapper** (`mvnw`, `mvnw.cmd`) — không bắt buộc cài Maven riêng.
> Mọi lệnh `mvn ...` trong tài liệu này có thể thay bằng `./mvnw ...` (Windows: `mvnw ...`).

### 1.2. IDE khuyến nghị

- **Backend**: IntelliJ IDEA (Community đủ dùng) với plugin Lombok bật sẵn.
- **Frontend**: VS Code với extension ESLint, Prettier, Tailwind CSS IntelliSense.

### 1.3. Cài đặt nhanh trên Ubuntu/Debian (tham khảo)

```bash
# JDK 21
sudo apt update
sudo apt install -y openjdk-21-jdk

# Maven
sudo apt install -y maven

# Node.js 20 (qua nvm — khuyến nghị hơn cài trực tiếp)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
source ~/.bashrc
nvm install 20
nvm use 20

# Docker + Docker Compose plugin
sudo apt install -y docker.io docker-compose-plugin
sudo usermod -aG docker $USER   # cần logout/login lại để có hiệu lực
```

### 1.4. Cài đặt trên macOS (tham khảo, dùng Homebrew)

```bash
brew install openjdk@21 maven node@20 docker docker-compose git
```

### 1.5. Cài đặt trên Windows

- Cài JDK 21 từ Adoptium/Eclipse Temurin.
- Cài Maven, thêm vào biến môi trường `PATH`.
- Cài Node.js 20 LTS từ trang chủ nodejs.org.
- Cài Docker Desktop (bật WSL2 backend).
- Khuyến nghị dùng WSL2 (Ubuntu) để các lệnh shell trong tài liệu này chạy đúng như trên Linux.

---

## 2. CẤU TRÚC THƯ MỤC DỰ ÁN

> ⚠️ Thực tế: repo backend **là chính thư mục này** (`chatsphere/chatsphere-backend/`), không có
> thư mục con `backend/`. 5 file tài liệu nằm ngay ở gốc repo (không nằm trong `docs/`).
> Frontend sẽ là repo/thư mục riêng, tạo sau (mục 7).

```
chatsphere/
└── chatsphere-backend/               # ← repo hiện tại (Spring Boot 4.1.1, Java 21)
    ├── src/
    │   ├── main/
    │   │   ├── java/com/chatsphere/          # package gốc = com.chatsphere
    │   │   │   ├── ChatsphereBackendApplication.java
    │   │   │   ├── auth/  user/  chat/  signaling/  presence/
    │   │   │   ├── notification/  media/
    │   │   │   ├── common/                   # BaseEntity, ApiResponse, exception...
    │   │   │   └── config/                   # Security/WebSocket/Redis/CORS config
    │   │   └── resources/
    │   │       ├── application.yaml          # ⚠️ đuôi .yaml (không phải .yml)
    │   │       ├── application-dev.yaml
    │   │       ├── application-prod.yaml
    │   │       └── db/migration/             # Flyway SQL scripts (V1__*.sql ...)
    │   └── test/java/com/chatsphere/
    ├── infra/
    │   ├── docker-compose.yml               # Postgres, Redis, MinIO, coturn, MailHog
    │   └── coturn/turnserver.conf
    ├── pom.xml
    ├── mvnw / mvnw.cmd                       # Maven Wrapper
    ├── .env.example  .env                    # .env đã nằm trong .gitignore
    ├── README.md
    ├── 01_SYSTEM_DESIGN.md ... 05_CLAUDE_CODE_SKILL.md
    └── .claude/skills/che-do-lam-viec/       # skill Explain/Direct mode cho Claude Code
```

> Đã có sẵn `.gitignore` / `.gitattributes` nhưng **chưa `git init`** — chạy `git init` tại
> thư mục `chatsphere-backend` khi muốn bắt đầu quản lý version.
> `nginx/` cho production xem `04_PRODUCTION_DEPLOYMENT.md`.

---

## 3. SETUP HẠ TẦNG BẰNG DOCKER COMPOSE

File `infra/docker-compose.yml` **đã có sẵn**.

> ⚠️ Thực tế — khác biệt so với bản kế hoạch ban đầu:
> - Bỏ dòng `version: "3.9"` (Compose v2 coi là obsolete).
> - Mật khẩu dev rút gọn: Postgres & Redis dùng `123456`.
> - MinIO dùng `minio_dev_password` (MinIO **bắt buộc** `MINIO_ROOT_PASSWORD` ≥ 8 ký tự — để `123456` sẽ crash-loop).
> - Postgres map ra **host port `5433`** (`5433:5432`) để không đụng dịch vụ khác đang chiếm `5432` trên máy;
>   connection string dev là `jdbc:postgresql://localhost:5433/chatsphere`.

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: chatsphere-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: chatsphere
      POSTGRES_USER: chatsphere
      POSTGRES_PASSWORD: 123456
    ports:
      - "5433:5432"   # host 5433 -> container 5432
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U chatsphere"]
      interval: 5s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7-alpine
    container_name: chatsphere-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    command: redis-server --requirepass 123456
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "123456", "ping"]
      interval: 5s
      timeout: 5s
      retries: 10

  minio:
    image: minio/minio:latest
    container_name: chatsphere-minio
    restart: unless-stopped
    environment:
      MINIO_ROOT_USER: chatsphere_admin
      MINIO_ROOT_PASSWORD: minio_dev_password   # ⚠️ tối thiểu 8 ký tự
    ports:
      - "9000:9000"   # API
      - "9001:9001"   # Console web
    volumes:
      - minio_data:/data
    command: server /data --console-address ":9001"

  coturn:
    image: coturn/coturn:latest
    container_name: chatsphere-coturn
    restart: unless-stopped
    network_mode: "host"     # coturn cần host network để xử lý đúng NAT
    volumes:
      - ./coturn/turnserver.conf:/etc/coturn/turnserver.conf

  mailhog:
    image: mailhog/mailhog:latest
    container_name: chatsphere-mailhog
    restart: unless-stopped
    ports:
      - "1025:1025"   # SMTP giả lập — dùng test email xác thực/quên mật khẩu
      - "8025:8025"   # Web UI xem email đã gửi

volumes:
  postgres_data:
  redis_data:
  minio_data:
```

**Giải thích lựa chọn:**
- `mailhog`: SMTP server giả lập cho môi trường dev — không cần tài khoản Gmail/SMTP thật để test tính năng gửi email (UC-01, UC-06). Xem email đã "gửi" tại `http://localhost:8025`.
- `minio`: giả lập S3 locally, tránh phải tạo tài khoản AWS khi mới học.
- `coturn` dùng `network_mode: host`: bắt buộc vì TURN relay cần cấp phát port động (relay port range) mà Docker bridge network không forward được linh hoạt.

Khởi động hạ tầng:

```bash
cd infra
docker compose up -d
docker compose ps        # kiểm tra tất cả service đều "healthy"/"running"
```

---

## 4. KHỞI TẠO BACKEND SPRING BOOT

> ⚠️ **Đã hoàn thành** — phần này ghi lại để tham khảo/tái tạo. `pom.xml` hiện tại đã có đủ.

### 4.1. Tạo project qua Spring Initializr (CLI)

```bash
curl https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=4.1.1 \
  -d baseDir=. \
  -d groupId=com.chatsphere \
  -d artifactId=chatsphere-backend \
  -d name=chatsphere-backend \
  -d packageName=com.chatsphere \
  -d javaVersion=21 \
  -d dependencies=web,websocket,security,data-jpa,postgresql,validation,data-redis,mail,actuator,devtools,flyway \
  -o chatsphere-backend.zip
unzip chatsphere-backend.zip
rm chatsphere-backend.zip
```

> ⚠️ Thực tế đang dùng **Spring Boot 4.1.1** (không phải 3.3.4). Ở Spring Boot 4 tên một số starter đổi:
> `spring-boot-starter-web` → `spring-boot-starter-webmvc`; Flyway là `spring-boot-starter-flyway`
> + `flyway-database-postgresql`; các starter test tách riêng (`spring-boot-starter-webmvc-test`,
> `-data-jpa-test`, `-security-test`...). Xem `pom.xml` để biết danh sách chính xác.
>
> Package gốc là **`com.chatsphere`** (class `ChatsphereBackendApplication`), các module nghiệp vụ
> nằm dưới `com.chatsphere.<module>` đúng theo `01_SYSTEM_DESIGN.md` mục 3.3.
>
> Nếu không có mạng để gọi `start.spring.io`, dùng IntelliJ IDEA → New Project → Spring Initializr.

### 4.2. Thêm dependency bổ sung vào `pom.xml`

Các dependency Initializr không có sẵn — **đã thêm** (version quản lý qua `<properties>` trong `pom.xml`):

| Dependency | Version đang dùng | Ghi chú |
|---|---|---|
| `io.jsonwebtoken:jjwt-api / -impl / -jackson` | `0.12.6` | JWT — Phase 1 |
| `org.mapstruct:mapstruct` (+ processor) | `1.6.3` | mapping DTO |
| `org.projectlombok:lombok` (+ `lombok-mapstruct-binding` `0.2.0`) | `1.18.42` | |
| `io.minio:minio` | `8.5.17` | Phase 5 |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | **`3.1.0`** | ⚠️ bản `2.x` KHÔNG chạy với Spring Boot 4 (Spring 7 / Jackson 3) — phải dùng `3.x` |
| `nl.martijndwars:web-push` | `5.1.2` | Phase 5 |
| `org.testcontainers:*` | `1.21.4` | ⚠️ Spring Boot 4 parent **không** quản lý version Testcontainers → phải `import` `testcontainers-bom` trong `<dependencyManagement>` |

Snippet gốc (giữ lại để đối chiếu — version thực tế xem bảng trên):

```xml
<dependencies>
    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.6</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.6</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.6</version>
        <scope>runtime</scope>
    </dependency>

    <!-- MapStruct -->
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>1.6.2</version>
    </dependency>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct-processor</artifactId>
        <version>1.6.2</version>
        <scope>provided</scope>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- MinIO SDK (S3-compatible) -->
    <dependency>
        <groupId>io.minio</groupId>
        <artifactId>minio</artifactId>
        <version>8.5.14</version>
    </dependency>

    <!-- OpenAPI/Swagger -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.6.0</version>
    </dependency>

    <!-- Web Push (VAPID) -->
    <dependency>
        <groupId>nl.martijndwars</groupId>
        <artifactId>web-push</artifactId>
        <version>5.1.1</version>
    </dependency>

    <!-- Testcontainers -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Lưu ý thứ tự annotation processor (Lombok + MapStruct dùng chung có thể xung đột)** — thêm vào `maven-compiler-plugin`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>1.18.34</version>
                    </path>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>1.6.2</version>
                    </path>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok-mapstruct-binding</artifactId>
                        <version>0.2.0</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

## 5. CẤU HÌNH BACKEND CHI TIẾT

### 5.1. `.env.example` (đặt tại gốc repo `chatsphere-backend/`)

File `.env.example` **đã có sẵn**; `.env` cũng đã tạo sẵn cho dev và **đã nằm trong `.gitignore`**
(cùng với `.env.local`, `.env.prod`). Nội dung:

```bash
# Database (⚠️ host port 5433)
DB_URL=jdbc:postgresql://localhost:5433/chatsphere
DB_USERNAME=chatsphere
DB_PASSWORD=123456

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=123456

# JWT — sinh dev: openssl rand -base64 32
JWT_SECRET=ZGV2LW9ubHktc2VjcmV0LWNoYW5nZS1tZS0zMi1ieXRlcy1taW5pbXVtLWxlbmd0aA==
JWT_ACCESS_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=604800000

# Mail (dev dùng MailHog)
MAIL_HOST=localhost
MAIL_PORT=1025

# MinIO
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=chatsphere_admin
MINIO_SECRET_KEY=minio_dev_password
MINIO_BUCKET=chatsphere-media

# TURN (khớp infra/coturn/turnserver.conf -> static-auth-secret)
TURN_SECRET=change_this_turn_shared_secret
TURN_SERVER_URL=turn:localhost:3478

# CORS (origin frontend Vite)
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

> ⚠️ `application-dev.yaml` đã có **giá trị mặc định** cho tất cả biến trên (khớp bảng này), nên khi
> hạ tầng Docker đang chạy thì `./mvnw spring-boot:run` chạy được **không cần** nạp `.env`.
> Chỉ cần `.env` khi muốn đổi giá trị. Sinh secret production riêng: `openssl rand -base64 64`.
>
> ⚠️ Nếu shell/máy đã có sẵn biến môi trường trùng tên (ví dụ `DB_PASSWORD` do project khác export)
> thì Spring sẽ ưu tiên biến đó và ghi đè default trong yaml → có thể lỗi `password authentication failed`.
> Kiểm tra: `env | grep -E 'DB_|REDIS_|SPRING_'`. Khắc phục: `unset DB_PASSWORD` hoặc nạp `.env` để đè lại.

### 5.2. `application.yaml` (cấu hình chung, không đổi theo môi trường)

> ⚠️ File thật tên `application.yaml` (đuôi `.yaml`). Có thêm `spring.profiles.default: dev` để local
> mặc định chạy profile `dev`; production set biến `SPRING_PROFILES_ACTIVE=prod`.

```yaml
spring:
  application:
    name: chatsphere-backend
  profiles:
    default: dev
  jackson:
    property-naming-strategy: SNAKE_CASE
    default-property-inclusion: non_null
  servlet:
    multipart:
      max-file-size: 25MB
      max-request-size: 25MB

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

### 5.3. `application-dev.yaml`

> ⚠️ File thật tên `application-dev.yaml`, và mỗi biến đều có **giá trị mặc định** dạng
> `${DB_URL:jdbc:postgresql://localhost:5433/chatsphere}` (khớp `infra/docker-compose.yml`).
> Cũng đã thêm `spring.jpa.open-in-view: false`.

```yaml
spring:
  config:
    activate:
      on-profile: dev

  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5433/chatsphere}
    username: ${DB_USERNAME:chatsphere}
    password: ${DB_PASSWORD:123456}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate       # Flyway quản lý schema, JPA chỉ validate khớp
    show-sql: true
    properties:
      hibernate:
        format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}

  mail:
    host: ${MAIL_HOST}
    port: ${MAIL_PORT}

app:
  jwt:
    secret: ${JWT_SECRET}
    access-expiration-ms: ${JWT_ACCESS_EXPIRATION_MS}
    refresh-expiration-ms: ${JWT_REFRESH_EXPIRATION_MS}
  minio:
    endpoint: ${MINIO_ENDPOINT}
    access-key: ${MINIO_ACCESS_KEY}
    secret-key: ${MINIO_SECRET_KEY}
    bucket: ${MINIO_BUCKET}
  turn:
    secret: ${TURN_SECRET}
    server-url: ${TURN_SERVER_URL}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS}

logging:
  level:
    com.chatsphere: DEBUG
    org.springframework.web.socket: DEBUG
```

> `application-prod.yaml` **đã có sẵn** trong repo (dựng theo `04_PRODUCTION_DEPLOYMENT.md` mục 6.1):
> `ddl-auto: validate`, `show-sql: false`, `open-in-view: false`, Hikari pool 20,
> `forward-headers-strategy: native`, actuator mở thêm `metrics,prometheus`, log `INFO`.

### 5.4. Chạy backend ở chế độ dev

Hạ tầng Docker phải đang chạy (mục 3). Từ **gốc repo `chatsphere-backend/`**:

```bash
./mvnw spring-boot:run
```

`spring.profiles.default: dev` nên không cần truyền `-Dspring-boot.run.profiles=dev`.
Các giá trị dev đã có default trong `application-dev.yaml` nên **không cần nạp `.env`**.

Muốn nạp `.env` (khi đã sửa giá trị) — Windows PowerShell:
```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*([^#=][^=]*)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim()) }
}
./mvnw spring-boot:run
```
Linux/macOS: `export $(grep -v '^#' .env | xargs) && ./mvnw spring-boot:run`

Kiểm tra backend sống: `curl http://localhost:8080/actuator/health` → kỳ vọng `{"status":"UP"}`.

> ⚠️ Nếu port `8080` đã bị dịch vụ khác chiếm: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8095`.
>
> ⚠️ **Swagger UI và các endpoint khác trả `401`** cho tới khi viết `SecurityConfig`
> (`03_CODE_ROADMAP.md` Phase 0.6 + Phase 1.2). Riêng `/actuator/health` mở sẵn nên vẫn `{"status":"UP"}`.

---

## 6. SETUP DATABASE & FLYWAY MIGRATION

### 6.1. Quy ước đặt tên file migration

```
src/main/resources/db/migration/          # thư mục đã tạo sẵn (hiện chỉ có .gitkeep)
├── V1__create_users_table.sql
├── V2__create_user_settings_table.sql
├── V3__create_friend_requests_and_friendships.sql
├── V4__create_blocked_users.sql
├── V5__create_conversations_and_participants.sql
├── V6__create_messages.sql
├── V7__create_message_attachments_reactions_deletions.sql
├── V8__create_call_sessions.sql
├── V9__create_notifications.sql
├── V10__create_refresh_tokens_and_push_subscriptions.sql
└── V11__add_fulltext_search_indexes.sql
```

### 6.2. Ví dụ `V1__create_users_table.sql`

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    bio VARCHAR(255),
    date_of_birth DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
```

> Toàn bộ 11 script migration tương ứng với 15 bảng đã mô tả ở mục 7 file `01_SYSTEM_DESIGN.md` sẽ được viết chi tiết trong quá trình code thực tế theo `03_CODE_ROADMAP.md` (Phase 1) — tài liệu này chỉ minh họa cấu trúc và quy ước.

### 6.3. Chạy migration thủ công (không qua Spring Boot, để kiểm tra nhanh)

```bash
./mvnw flyway:migrate \
  -Dflyway.url=jdbc:postgresql://localhost:5433/chatsphere \
  -Dflyway.user=chatsphere \
  -Dflyway.password=123456
```

Kiểm tra bảng đã tạo:
```bash
docker exec -it chatsphere-postgres psql -U chatsphere -d chatsphere -c "\dt"
```

> Hiện `db/migration/` chưa có script nào → Spring Boot khi khởi động chỉ log
> `No migrations found` / `Schema "public" is up to date` (bình thường ở giai đoạn này).

---

## 7. KHỞI TẠO FRONTEND REACT

> ❌ **Chưa làm** — frontend sẽ được khởi tạo sau, ở repo/thư mục riêng. Phần dưới giữ nguyên làm hướng dẫn.

### 7.1. Tạo project bằng Vite

```bash
cd ..   # về thư mục gốc chatsphere/
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
```

### 7.2. Cài đặt thư viện cần thiết

```bash
npm install axios react-router-dom zustand \
  @stomp/stompjs sockjs-client \
  react-hook-form zod @hookform/resolvers \
  date-fns clsx

npm install -D @types/sockjs-client tailwindcss postcss autoprefixer

npx tailwindcss init -p
```

### 7.3. Cấu hình Tailwind — `tailwind.config.js`

```js
/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: { extend: {} },
  plugins: [],
}
```

Thêm vào `src/index.css`:
```css
@tailwind base;
@tailwind components;
@tailwind utilities;
```

### 7.4. File môi trường frontend — `.env.development`

```bash
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_WS_URL=http://localhost:8080/ws
VITE_STUN_URL=stun:stun.l.google.com:19302
VITE_TURN_URL=turn:localhost:3478
```

### 7.5. Chạy frontend

```bash
npm run dev
```

Mặc định chạy tại `http://localhost:5173`.

---

## 8. CẤU HÌNH COTURN (TURN SERVER) CHO LOCAL

> ✅ File `infra/coturn/turnserver.conf` **đã có sẵn**.
> ⚠️ Lưu ý: nếu chạy `docker compose up` khi file này **chưa tồn tại**, Docker sẽ tự tạo
> `turnserver.conf` thành một **thư mục** (bind mount) → coturn đọc sai cấu hình. Nếu gặp:
> `docker compose down`, xóa thư mục rỗng đó, tạo lại file, `docker compose up -d`.

Nội dung `infra/coturn/turnserver.conf`:

```ini
listening-port=3478
tls-listening-port=5349
min-port=49152
max-port=65535

# Dùng shared secret thay vì user/pass tĩnh (khớp mục 9.3 file 01)
use-auth-secret
static-auth-secret=change_this_turn_shared_secret

realm=chatsphere.local
server-name=chatsphere-turn

# Địa chỉ IP public của máy dev (localhost dùng 127.0.0.1 khi test 1 máy)
listening-ip=0.0.0.0
relay-ip=127.0.0.1

# Log để debug khi mới học
verbose
log-file=stdout

# Tắt các tính năng không cần khi dev
no-cli
no-tlsv1
no-tlsv1_1
```

> **Lưu ý quan trọng khi test 2 máy thật (không phải cùng localhost):** thay `relay-ip` bằng IP LAN/public thực của máy chạy coturn, và mở port UDP/TCP 3478 + dải `min-port`-`max-port` trên firewall/router. Test video call giữa 2 mạng khác nhau (ví dụ 1 máy dùng wifi, 1 máy dùng 4G hotspot) là cách tốt nhất để thấy rõ vai trò của TURN — nếu chỉ test 2 tab trên cùng máy, kết nối luôn thành công qua `localhost` mà không cần STUN/TURN, nên sẽ không thấy hết được giá trị của bài học này.

### 8.1. Sinh TURN credential tạm thời (test bằng tay)

```bash
# Ví dụ dùng Python để test thuật toán HMAC time-limited credential
python3 - <<'EOF'
import hmac, hashlib, base64, time

secret = "change_this_turn_shared_secret"
username = str(int(time.time()) + 3600)  # hết hạn sau 1 giờ
key = hmac.new(secret.encode(), username.encode(), hashlib.sha1).digest()
credential = base64.b64encode(key).decode()

print("username:", username)
print("credential:", credential)
EOF
```

Backend sẽ tự sinh credential này qua endpoint `/api/v1/calls/ice-servers` (chi tiết implement ở `03_CODE_ROADMAP.md` Phase 5).

---

## 9. CHẠY THỬ TOÀN BỘ HỆ THỐNG

Thứ tự khởi động khuyến nghị mỗi khi phát triển:

```bash
# 1. Hạ tầng (từ gốc repo chatsphere-backend/)
cd infra && docker compose up -d && cd ..

# 2. Backend
./mvnw spring-boot:run

# 3. Frontend (chưa có — làm sau)
# cd ../chatsphere-frontend && npm run dev
```

Truy cập:
- Health:            `http://localhost:8080/actuator/health` → `{"status":"UP"}`
- Swagger API docs:  `http://localhost:8080/swagger-ui.html`  *(401 tới khi có SecurityConfig — Phase 1)*
- MailHog:           `http://localhost:8025`
- MinIO Console:     `http://localhost:9001`  (`chatsphere_admin` / `minio_dev_password`)
- Postgres:          `localhost:5433`  (⚠️ 5433, không phải 5432)

---

## 10. KIỂM TRA (VERIFICATION CHECKLIST)

Đánh dấu từng mục sau khi setup xong trước khi chuyển sang viết code (file `03_CODE_ROADMAP.md`):

- [x] `docker compose ps` — postgres, redis, minio, coturn, mailhog đều `Up` (postgres/redis `healthy`).
- [x] `./mvnw clean compile` chạy không lỗi.
- [x] `./mvnw spring-boot:run` → log `Started ChatsphereBackendApplication`; `curl http://localhost:8080/actuator/health` = `{"status":"UP"}`.
- [ ] `./mvnw flyway:migrate` chạy không lỗi (chưa có script nào → "No migrations found", bình thường).
- [ ] `http://localhost:8080/swagger-ui.html` — hiện trả `401`; sẽ xem được sau khi có `SecurityConfig` (Phase 1).
- [ ] `docker exec -it chatsphere-redis redis-cli -a 123456 ping` trả về `PONG`.
- [ ] MinIO Console `http://localhost:9001`, đăng nhập `chatsphere_admin` / `minio_dev_password`, tạo bucket `chatsphere-media`.
- [ ] Gửi thử email qua SMTP `localhost:1025`, kiểm tra ở `http://localhost:8025`.
- [ ] `docker logs chatsphere-coturn` không có lỗi khởi động.
- [ ] *(frontend — làm sau)* `npm run dev` mở `http://localhost:5173`.
- [ ] *(Phase 0.8)* `./mvnw test` — hiện `contextLoads` **fail** vì cần DB + Testcontainers chưa cấu hình; sẽ xử lý ở `03_CODE_ROADMAP.md` Phase 0.8 / 0.10.

---

## 11. XỬ LÝ SỰ CỐ THƯỜNG GẶP (TROUBLESHOOTING)

| Sự cố | Nguyên nhân thường gặp | Cách khắc phục |
|---|---|---|
| `Connection refused` khi backend kết nối Postgres | Container chưa healthy khi backend start | Chờ `docker compose ps` báo `healthy`, hoặc thêm `depends_on.condition: service_healthy` |
| Lombok không hoạt động trong IntelliJ | Chưa bật annotation processing | Settings → Build → Compiler → Annotation Processors → Enable |
| Lỗi `port 5432 already in use` | Có PostgreSQL/container khác đang chiếm `5432` | **Đã xử lý**: compose map `5433:5432`, connection string dev dùng `localhost:5433` |
| `password authentication failed for user "chatsphere"` (dù mật khẩu đúng) | (a) volume `postgres_data` đã init bằng mật khẩu cũ — đổi `POSTGRES_PASSWORD` sau đó không có tác dụng; (b) shell có sẵn biến `DB_PASSWORD` khác đè lên default | (a) `docker compose down -v` rồi `up -d` để tạo lại volume (chỉ làm khi chưa có dữ liệu quan trọng); (b) `unset DB_PASSWORD` hoặc nạp `.env` |
| MinIO container `Restarting` liên tục | `MINIO_ROOT_PASSWORD` ngắn hơn 8 ký tự | Đặt mật khẩu ≥ 8 ký tự (`minio_dev_password`) rồi `docker compose up -d --force-recreate minio` |
| `infra/coturn/turnserver.conf` là thư mục rỗng | Chạy `docker compose up` khi file chưa tồn tại → Docker tự tạo thành thư mục | `docker compose down`, xóa thư mục, tạo lại file `.conf`, `up -d` |
| Port `8080` in use khi `spring-boot:run` | Dịch vụ khác chiếm 8080 | `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8095` |
| `springdoc` / Swagger lỗi `NoSuchMethodError` khi nâng Spring Boot 4 | Còn dùng `springdoc-openapi 2.x` (cho Spring Boot 3) | Dùng `springdoc-openapi-starter-webmvc-ui` **3.x** |
| `pom.xml` lỗi `dependencies.dependency.version ... is missing` cho `org.testcontainers` | Spring Boot 4 parent không quản lý version Testcontainers | Import `testcontainers-bom` trong `<dependencyManagement>` (đã làm) |
| WebSocket không connect được từ frontend | CORS/SockJS endpoint chưa cấu hình đúng | Kiểm tra `WebSocketConfig` đã `setAllowedOrigins` đúng domain frontend |
| coturn không hoạt động khi test 2 máy khác mạng | `relay-ip` vẫn để `127.0.0.1` hoặc firewall chặn port range | Đổi `relay-ip` thành IP thật, mở port UDP 49152-65535 |
| MapStruct không generate mapper | Thiếu `lombok-mapstruct-binding` hoặc sai thứ tự annotationProcessorPaths | Kiểm tra lại cấu hình mục 4.2, chạy `mvn clean compile` |
| Flyway báo `checksum mismatch` | Đã sửa file migration cũ sau khi chạy | Không bao giờ sửa migration đã chạy — tạo migration mới để thay đổi (nguyên tắc bất biến của Flyway) |
| `getUserMedia` bị từ chối trên trình duyệt | WebRTC yêu cầu HTTPS hoặc `localhost` | Dev trên `localhost` là ngoại lệ được phép; khi deploy thử trên IP LAN cần HTTPS |

---

*Hết tài liệu 02_SETUP_GUIDE.md — tiếp theo xem `03_CODE_ROADMAP.md` để bắt đầu code theo lộ trình.*
