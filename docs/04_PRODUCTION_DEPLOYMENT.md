# TÀI LIỆU ĐƯA VÀO PRODUCTION
## ChatSphere — Hướng dẫn triển khai môi trường thật (Production Deployment Guide)

**Tài liệu liên quan:** `01_SYSTEM_DESIGN.md`, `02_SETUP_GUIDE.md`, `03_CODE_ROADMAP.md` (đã hoàn thành toàn bộ code trước khi đọc file này)

---

## MỤC LỤC

1. [Nguyên tắc chung khi lên Production](#1-nguyên-tắc-chung-khi-lên-production)
2. [Lựa chọn hạ tầng (Hosting)](#2-lựa-chọn-hạ-tầng-hosting)
3. [Kiến trúc Production](#3-kiến-trúc-production)
4. [Chuẩn bị Domain, SSL/TLS](#4-chuẩn-bị-domain-ssltls)
5. [Đóng gói ứng dụng (Docker)](#5-đóng-gói-ứng-dụng-docker)
6. [Cấu hình Backend cho Production](#6-cấu-hình-backend-cho-production)
7. [Reverse Proxy & Nginx](#7-reverse-proxy--nginx)
8. [TURN Server Production (coturn)](#8-turn-server-production-coturn)
9. [Database Production](#9-database-production)
10. [CI/CD Pipeline](#10-cicd-pipeline)
11. [Scaling & Load Balancing](#11-scaling--load-balancing)
12. [Bảo mật Production](#12-bảo-mật-production)
13. [Monitoring & Logging](#13-monitoring--logging)
14. [Backup & Disaster Recovery](#14-backup--disaster-recovery)
15. [Chi phí ước tính](#15-chi-phí-ước-tính)
16. [Checklist trước khi go-live](#16-checklist-trước-khi-go-live)

---

## 1. NGUYÊN TẮC CHUNG KHI LÊN PRODUCTION

- **Không bao giờ** dùng lại mật khẩu/secret của môi trường dev (`.env` dev) cho production — sinh secret mới hoàn toàn.
- **Không bao giờ** commit file `.env`, key, certificate vào Git — dùng secret manager hoặc biến môi trường của platform hosting.
- Mọi kết nối phải qua **HTTPS/WSS** — trình duyệt hiện đại chặn `getUserMedia()` trên trang không an toàn (ngoại lệ `localhost`), nên video call **bắt buộc** phải chạy trên HTTPS khi production.
- Áp dụng nguyên tắc **12-Factor App**: cấu hình qua biến môi trường, log ra stdout, stateless ở tầng ứng dụng (trạng thái thật sự nằm ở DB/Redis).

---

## 2. LỰA CHỌN HẠ TẦNG (HOSTING)

Vì đây là dự án học tập cá nhân, ưu tiên phương án **chi phí thấp, dễ vận hành**, có đường nâng cấp rõ ràng nếu sau này muốn mở rộng.

| Phương án | Ưu điểm | Nhược điểm | Khuyến nghị |
|---|---|---|---|
| **VPS đơn (DigitalOcean/Vultr/Linode/Hetzner), tự triển khai Docker Compose** | Chi phí thấp nhất (~5-10 USD/tháng cho cấu hình nhỏ), toàn quyền kiểm soát, học được cách tự vận hành hạ tầng | Tự chịu trách nhiệm bảo trì, backup, security patching | **Recommended cho mục tiêu học tập** — hiểu rõ toàn bộ pipeline triển khai |
| PaaS (Render, Railway, Fly.io) | Triển khai nhanh, tự động SSL, ít việc vận hành | coturn (TURN server) khó chạy đúng trên PaaS vì cần cấp phát dải port UDP động — nhiều PaaS không hỗ trợ | Phù hợp nếu chỉ muốn deploy nhanh phần chat, dùng TURN service bên ngoài (ví dụ Twilio TURN, Metered.ca) cho riêng phần video call |
| Kubernetes (K8s) | Scale mạnh, chuẩn công nghiệp | Quá phức tạp so với nhu cầu 1 dự án học tập cá nhân giai đoạn đầu | Không khuyến nghị ở giai đoạn này — có thể học sau khi đã vững Docker Compose |
| Serverless (AWS Lambda...) | Không cần quản lý server | WebSocket long-lived connection và coturn hoàn toàn không phù hợp với mô hình serverless | Không phù hợp cho ứng dụng này |

**Khuyến nghị cụ thể**: 1 VPS (tối thiểu 2 vCPU / 4GB RAM) chạy Docker Compose gồm: Nginx (reverse proxy) + Spring Boot (backend, có thể 1-2 instance) + PostgreSQL + Redis + coturn + frontend (build tĩnh serve qua Nginx).

> Nếu muốn tách bạch quản lý, có thể dùng **managed PostgreSQL** (DigitalOcean Managed Database) để không phải tự lo backup/patching DB — đây là hạng mục nên "thuê ngoài" đầu tiên vì rủi ro mất dữ liệu cao nhất.

---

## 3. KIẾN TRÚC PRODUCTION

```
                        Internet
                           │
                    ┌──────▼──────┐
                    │   Nginx      │  (reverse proxy, SSL termination, static file serving)
                    │  :443 (HTTPS)│
                    └──────┬──────┘
                 ┌─────────┼─────────┐
                 ▼         ▼         ▼
         [Frontend static] [/api/*] [/ws]
                          │         │
                 ┌────────▼─────────▼────────┐
                 │   Spring Boot (instance 1)  │──┐
                 │   Spring Boot (instance 2)  │──┤ (load balance qua Nginx upstream)
                 └────────┬─────────┬────────┘  │
                          │         │            │
              ┌───────────▼──┐  ┌───▼────────────▼─┐
              │  PostgreSQL   │  │  Redis (session,   │
              │  (Primary +   │  │  presence, Pub/Sub  │
              │  Replica tùy) │  │  cho STOMP relay)   │
              └───────────────┘  └─────────────────────┘

                 [coturn] — chạy network_mode: host, port UDP/TCP 3478 + 49152-65535
                     │
                Internet (media relay khi cần)
```

**Điểm khác biệt quan trọng so với kiến trúc dev**: khi chạy **nhiều instance Spring Boot** (để chịu tải cao hơn / high availability), cần dùng **Redis làm STOMP message broker relay** thay vì "Simple Broker" mặc định trong bộ nhớ — nếu không, user kết nối vào instance A sẽ không nhận được tin nhắn broadcast từ instance B. Cấu hình bằng cách bật `spring-boot-starter-websocket` kết hợp thư viện `spring-session-data-redis` hoặc chuyển sang broker ngoài như RabbitMQ/ActiveMQ (STOMP relay đầy đủ) nếu cần mở rộng lớn hơn nữa.

---

## 4. CHUẨN BỊ DOMAIN, SSL/TLS

### 4.1. Domain

- Mua domain (ví dụ `chatsphere.app`) từ Namecheap/Cloudflare Registrar.
- Trỏ DNS record:
  - `A record`: `chatsphere.app` → IP VPS.
  - `A record`: `turn.chatsphere.app` → IP VPS (hoặc IP riêng nếu coturn chạy máy khác).

### 4.2. SSL/TLS bằng Let's Encrypt (Certbot)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d chatsphere.app -d www.chatsphere.app
```

Certbot tự động cấu hình Nginx dùng chứng chỉ và thiết lập cron job gia hạn tự động (chứng chỉ Let's Encrypt hết hạn sau 90 ngày).

Kiểm tra tự động gia hạn hoạt động:
```bash
sudo certbot renew --dry-run
```

### 4.3. Chứng chỉ cho coturn (TLS TURN — khuyến nghị)

```bash
sudo certbot certonly --standalone -d turn.chatsphere.app
```

Trỏ `cert-file`/`pkey-file` trong `turnserver.conf` đến các file certificate vừa tạo (mục 8 bên dưới).

---

## 5. ĐÓNG GÓI ỨNG DỤNG (DOCKER)

### 5.1. `backend/Dockerfile` (multi-stage build)

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY ../pom.xml .
RUN mvn dependency:go-offline -B
COPY ../src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime (image nhỏ gọn, không chứa Maven/JDK build tool)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

**Giải thích lựa chọn:**
- Multi-stage build: image cuối cùng không chứa Maven + source code build tool, giảm kích thước và bề mặt tấn công.
- Chạy bằng user non-root (`spring`): nguyên tắc bảo mật container cơ bản.
- `-XX:MaxRAMPercentage=75.0`: giới hạn heap JVM theo % RAM được cấp cho container, tránh JVM cấp phát vượt giới hạn container và bị OOM-killed.

### 5.2. `frontend/Dockerfile`

```dockerfile
# Stage 1: Build static files
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Serve bằng Nginx nhẹ
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### 5.3. `docker-compose.prod.yml`

```yaml
version: "3.9"

services:
  nginx:
    image: nginx:1.27-alpine
    restart: always
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
    depends_on:
      - backend
      - frontend

  frontend:
    build: ./frontend
    restart: always

  backend:
    build: ./backend
    restart: always
    env_file: .env.prod
    deploy:
      replicas: 2
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy

  postgres:
    image: postgres:16-alpine
    restart: always
    env_file: .env.prod
    volumes:
      - postgres_prod_data:/var/lib/postgresql/data
      - ./backups:/backups
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME}"]
      interval: 10s
      retries: 5

  redis:
    image: redis:7-alpine
    restart: always
    command: redis-server --requirepass ${REDIS_PASSWORD} --appendonly yes
    volumes:
      - redis_prod_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      retries: 5

  coturn:
    image: coturn/coturn:latest
    restart: always
    network_mode: "host"
    volumes:
      - ./coturn/turnserver.conf:/etc/coturn/turnserver.conf
      - /etc/letsencrypt:/etc/letsencrypt:ro

volumes:
  postgres_prod_data:
  redis_prod_data:
```

> **Lưu ý**: `deploy.replicas` chỉ có hiệu lực khi chạy qua Docker Swarm (`docker stack deploy`). Nếu chỉ dùng `docker compose up` thuần, cần khai báo tường minh 2 service `backend-1`, `backend-2` hoặc chuyển sang Swarm/K8s — xem mục 11 để biết khi nào thực sự cần bước này.

---

## 6. CẤU HÌNH BACKEND CHO PRODUCTION

### 6.1. `application-prod.yml`

```yaml
spring:
  config:
    activate:
      on-profile: prod

  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate   # BẮT BUỘC — không bao giờ dùng update/create ở production
    show-sql: false

  flyway:
    enabled: true

  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}
      ssl:
        enabled: ${REDIS_SSL_ENABLED:false}

server:
  port: 8080
  forward-headers-strategy: native   # để Spring hiểu đúng scheme HTTPS đến từ Nginx (X-Forwarded-Proto)

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: never   # không lộ chi tiết hạ tầng ra public

logging:
  level:
    root: INFO
    com.chatsphere: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

app:
  cors:
    allowed-origins: https://chatsphere.app
```

### 6.2. Biến môi trường bắt buộc (`.env.prod`) — KHÔNG commit vào Git

```bash
DB_URL=jdbc:postgresql://postgres:5432/chatsphere
DB_USERNAME=chatsphere_prod
DB_PASSWORD=<sinh_ngau_nhien_manh>

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=<sinh_ngau_nhien_manh>

JWT_SECRET=<sinh_bang_openssl_rand_base64_64>
JWT_ACCESS_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=604800000

TURN_SECRET=<sinh_ngau_nhien_manh_khac_voi_dev>
TURN_SERVER_URL=turn:turn.chatsphere.app:3478

MINIO_ENDPOINT=https://storage.chatsphere.app
MINIO_ACCESS_KEY=<key_that>
MINIO_SECRET_KEY=<secret_that>

MAIL_HOST=<smtp_that_vi_du_sendgrid_hoac_ses>
MAIL_PORT=587
MAIL_USERNAME=<smtp_username>
MAIL_PASSWORD=<smtp_password>

CORS_ALLOWED_ORIGINS=https://chatsphere.app
```

> Sinh secret mạnh: `openssl rand -base64 64`. Lưu trữ `.env.prod` bằng công cụ quản lý secret (Doppler, HashiCorp Vault, hoặc tối thiểu là file có quyền `chmod 600`, chỉ user deploy đọc được) — không bao giờ để trong repo Git kể cả private repo.

---

## 7. REVERSE PROXY & NGINX

### 7.1. `nginx/nginx.conf`

```nginx
events { worker_connections 1024; }

http {
    upstream backend_upstream {
        server backend:8080;
        # Khi scale nhiều instance (mục 11), thêm:
        # server backend2:8080;
    }

    # Redirect HTTP -> HTTPS
    server {
        listen 80;
        server_name chatsphere.app www.chatsphere.app;
        return 301 https://$host$request_uri;
    }

    server {
        listen 443 ssl http2;
        server_name chatsphere.app www.chatsphere.app;

        ssl_certificate /etc/letsencrypt/live/chatsphere.app/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/chatsphere.app/privkey.pem;
        ssl_protocols TLSv1.2 TLSv1.3;

        # Frontend static files
        location / {
            proxy_pass http://frontend:80;
        }

        # REST API
        location /api/ {
            proxy_pass http://backend_upstream;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto https;
        }

        # WebSocket — QUAN TRỌNG: cần header Upgrade/Connection và timeout dài
        location /ws/ {
            proxy_pass http://backend_upstream;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_set_header X-Forwarded-Proto https;
            proxy_read_timeout 3600s;   # giữ kết nối WebSocket lâu dài, mặc định Nginx timeout 60s sẽ ngắt kết nối chat
            proxy_send_timeout 3600s;
        }

        client_max_body_size 25M;   # khớp giới hạn upload file (mục 8.1 file 01)
    }
}
```

> **Lỗi thường gặp nhất khi đưa chat real-time lên production**: quên cấu hình `proxy_read_timeout` dài cho location `/ws/` → Nginx tự ngắt kết nối WebSocket sau 60 giây mặc định, khiến chat "tự nhiên bị mất kết nối" định kỳ dù code backend hoàn toàn không lỗi.

---

## 8. TURN SERVER PRODUCTION (COTURN)

### 8.1. `turnserver.conf` production

```ini
listening-port=3478
tls-listening-port=5349
min-port=49152
max-port=65535

use-auth-secret
static-auth-secret=${TURN_SECRET}

realm=chatsphere.app
server-name=chatsphere-turn

# IP thật của VPS (bắt buộc đúng — khác với dev dùng 127.0.0.1)
external-ip=<IP_PUBLIC_CUA_VPS>
relay-ip=<IP_PUBLIC_CUA_VPS>

# TLS certificate (dùng chung Let's Encrypt đã tạo ở mục 4.3)
cert=/etc/letsencrypt/live/turn.chatsphere.app/fullchain.pem
pkey=/etc/letsencrypt/live/turn.chatsphere.app/privkey.pem

# Bảo mật — chặn relay tới các dải IP nội bộ (chống lạm dụng TURN làm proxy tấn công mạng nội bộ)
denied-peer-ip=10.0.0.0-10.255.255.255
denied-peer-ip=172.16.0.0-172.31.255.255
denied-peer-ip=192.168.0.0-192.168.255.255

# Giới hạn tài nguyên tránh bị lạm dụng
total-quota=100
bps-capacity=0
stale-nonce=600

no-cli
fingerprint
```

### 8.2. Mở firewall

```bash
sudo ufw allow 3478/tcp
sudo ufw allow 3478/udp
sudo ufw allow 5349/tcp
sudo ufw allow 5349/udp
sudo ufw allow 49152:65535/udp
```

### 8.3. Kiểm tra TURN hoạt động

Dùng công cụ Trickle ICE của webrtc.org (`https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/`) nhập TURN server + credential sinh từ endpoint `/api/v1/calls/ice-servers` để xác nhận có candidate loại `relay` được sinh ra thành công.

### 8.4. Phương án thay thế: TURN-as-a-Service

Nếu việc tự vận hành coturn (mở port, gia hạn cert, giám sát) quá tốn thời gian so với mục tiêu học tập hiện tại, có thể dùng dịch vụ TURN trả phí/free-tier như **Metered.ca** hoặc **Twilio Network Traversal Service** — chỉ cần thay đổi danh sách `iceServers` trả về từ `IceServerService`, toàn bộ phần signaling tự viết ở Phase 6/7 **không cần thay đổi gì** vì đã được thiết kế tách biệt (đây là điểm mạnh của việc tách rõ signaling khỏi STUN/TURN ngay từ đầu).

---

## 9. DATABASE PRODUCTION

### 9.1. Tùy chọn triển khai

| Phương án | Khi nào dùng |
|---|---|
| PostgreSQL tự host trong Docker Compose (như mục 5.3) | Ngân sách thấp nhất, chấp nhận tự backup/patch |
| Managed PostgreSQL (DigitalOcean/AWS RDS/Supabase) | Khuyến nghị khi có ngân sách — giảm rủi ro mất dữ liệu, tự động backup, patching |

### 9.2. Connection Pooling

- HikariCP (mặc định trong Spring Boot) đã cấu hình ở mục 6.1 — điều chỉnh `maximum-pool-size` theo công thức tham khảo: `((core_count * 2) + effective_spindle_count)`, thường 10-20 là đủ cho VPS nhỏ.

### 9.3. Backup định kỳ (nếu tự host)

Script `backup.sh` chạy qua cron hàng ngày:

```bash
#!/bin/bash
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
docker exec chatsphere-postgres pg_dump -U chatsphere_prod chatsphere | gzip > /backups/chatsphere_$TIMESTAMP.sql.gz

# Xóa backup cũ hơn 30 ngày
find /backups -name "*.sql.gz" -mtime +30 -delete

# (Khuyến nghị) đồng bộ lên object storage ngoài VPS để tránh mất cả server lẫn backup cùng lúc
# aws s3 cp /backups/chatsphere_$TIMESTAMP.sql.gz s3://chatsphere-backups/
```

Đăng ký cron: `crontab -e` → `0 2 * * * /path/to/backup.sh` (chạy 2h sáng hàng ngày).

### 9.4. Migration khi deploy

Flyway tự chạy migration khi backend khởi động (`spring.flyway.enabled=true`) — đảm bảo **luôn backup DB trước khi deploy version có migration mới**, và kiểm tra migration đã test kỹ ở môi trường staging trước khi áp dụng production.

---

## 10. CI/CD PIPELINE

### 10.1. GitHub Actions — `.github/workflows/ci.yml`

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  backend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Run tests
        run: cd backend && mvn test
      - name: Build jar
        run: cd backend && mvn clean package -DskipTests

  frontend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
      - run: cd frontend && npm ci
      - run: cd frontend && npm run lint
      - run: cd frontend && npm run build
```

### 10.2. `.github/workflows/deploy.yml` (deploy khi merge vào `main`)

```yaml
name: Deploy Production

on:
  push:
    branches: [main]

jobs:
  deploy:
    needs: []
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Deploy via SSH
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.VPS_SSH_KEY }}
          script: |
            cd /opt/chatsphere
            git pull origin main
            docker compose -f docker-compose.prod.yml build
            docker compose -f docker-compose.prod.yml up -d
            docker image prune -f
```

> Lưu các secret (`VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`) trong GitHub Repository Settings → Secrets and variables → Actions, không hardcode trong workflow file.

### 10.3. Chiến lược Deploy

- **Rolling update thủ công đơn giản** (phù hợp quy mô hiện tại): `docker compose up -d` sẽ tự tái tạo container thay đổi, có gián đoạn ngắn (vài giây) — chấp nhận được với dự án cá nhân.
- **Zero-downtime deploy** (nâng cao, khi cần): dùng Nginx `upstream` với 2 backend instance, deploy lần lượt từng instance (drain traffic khỏi instance đang update trước).

---

## 11. SCALING & LOAD BALANCING

### 11.1. Khi nào cần scale

Với dự án học tập/demo, 1 instance Spring Boot (2 vCPU/4GB RAM) đã đủ phục vụ hàng trăm kết nối WebSocket đồng thời. Chỉ cần cân nhắc scale ngang khi:
- Số kết nối WebSocket đồng thời vượt ngưỡng 1 instance xử lý được (theo dõi qua metric `tomcat.threads.busy`).
- Cần high-availability (không muốn downtime khi restart/deploy).

### 11.2. Scale ngang Backend (nhiều instance)

**Vấn đề cốt lõi**: Simple Broker mặc định của Spring WebSocket lưu subscription trong bộ nhớ của từng instance — user A kết nối vào instance 1, user B kết nối vào instance 2, nếu A gửi tin nhắn thì instance 1 không biết cách nào để đẩy tới session của B đang nằm ở instance 2.

**Giải pháp**: chuyển sang **External STOMP Broker** hỗ trợ đầy đủ giao thức STOMP (RabbitMQ với plugin STOMP, hoặc ActiveMQ Artemis) thay cho Simple Broker:

```yaml
spring:
  websocket:
    broker-relay:
      enabled: true
      host: rabbitmq
      port: 61613
      client-login: chatsphere
      client-passcode: ${RABBITMQ_PASSWORD}
```

Khi đó tất cả instance backend đều publish/subscribe qua RabbitMQ trung tâm, đảm bảo tin nhắn đến đúng người dùng bất kể họ đang kết nối vào instance nào.

### 11.3. Scale Database

- Đọc nhiều/ghi ít (điển hình ứng dụng chat: đọc lịch sử tin nhắn nhiều hơn ghi): cân nhắc **read replica** PostgreSQL khi tải cao, route query đọc sang replica.
- Với quy mô dự án học tập, chưa cần thiết ở giai đoạn đầu.

### 11.4. Scale coturn

- coturn tự thân chịu tải tốt (viết bằng C, hiệu năng cao). Nếu cần, có thể chạy nhiều instance coturn sau load balancer UDP (phức tạp) — thường không cần thiết cho tới khi có hàng nghìn cuộc gọi đồng thời.

---

## 12. BẢO MẬT PRODUCTION

| Hạng mục | Hành động cụ thể |
|---|---|
| Firewall | Chỉ mở port cần thiết: 80, 443, 3478/udp+tcp, 5349, 49152-65535/udp. Đóng toàn bộ port DB/Redis ra internet (chỉ nội bộ Docker network) |
| SSH | Vô hiệu hóa đăng nhập root, dùng SSH key thay password, đổi port SSH mặc định (tùy chọn) |
| Rate limiting | Cấu hình `limit_req` trong Nginx cho endpoint `/api/v1/auth/login`, `/api/v1/auth/register` chống spam/brute-force ở tầng proxy (bổ sung cho rate limit ở tầng ứng dụng đã làm ở Phase 1) |
| Security headers | Thêm vào Nginx: `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy` |
| Dependency scanning | Chạy `mvn dependency-check:check` và `npm audit` định kỳ trong CI |
| Secrets rotation | Xoay vòng `JWT_SECRET`, `TURN_SECRET`, mật khẩu DB định kỳ (ví dụ mỗi 90 ngày) |
| Container security | Image chạy non-root user (đã làm ở mục 5.1), quét lỗ hổng image bằng `trivy` |
| Giám sát đăng nhập bất thường | Log + cảnh báo khi có nhiều lần đăng nhập thất bại từ 1 IP trong thời gian ngắn |
| GDPR/quyền riêng tư cơ bản | Có endpoint cho user tự xóa tài khoản (xóa/ẩn danh dữ liệu cá nhân), chính sách retention rõ ràng cho log |

---

## 13. MONITORING & LOGGING

### 13.1. Metrics — Prometheus + Grafana

- Spring Boot Actuator đã expose `/actuator/prometheus` (cấu hình mục 6.1) nhờ dependency `micrometer-registry-prometheus`.
- `docker-compose.monitoring.yml` bổ sung Prometheus scrape endpoint này, Grafana dashboard hiển thị: số request/giây, độ trễ, số kết nối WebSocket đang mở, JVM heap usage.

### 13.2. Log tập trung

- Với quy mô 1 VPS: dùng `docker compose logs -f` hoặc mount volume log + `logrotate` là đủ.
- Khi cần nâng cao: đẩy log qua Loki (nhẹ hơn ELK, dễ tích hợp Grafana có sẵn).

### 13.3. Alerting

- Cấu hình Grafana Alerting hoặc Uptime Kuma (self-host, nhẹ) để nhận thông báo (Telegram/Email) khi:
  - `/actuator/health` trả về `DOWN`.
  - Disk usage VPS > 85%.
  - Số lỗi 5xx tăng đột biến.

### 13.4. Application Performance Monitoring (tùy chọn nâng cao)

- Tích hợp OpenTelemetry để trace luồng request xuyên suốt Controller → Service → Repository, hữu ích khi debug độ trễ trong luồng gửi tin nhắn/signaling.

---

## 14. BACKUP & DISASTER RECOVERY

| Thành phần | Chiến lược backup | Tần suất | RPO/RTO mục tiêu |
|---|---|---|---|
| PostgreSQL | `pg_dump` tự động (mục 9.3) + đồng bộ ngoài VPS | Hàng ngày | RPO 24h, RTO vài giờ (chấp nhận được cho dự án cá nhân) |
| MinIO/Object Storage | Bật versioning, hoặc rsync định kỳ sang storage khác | Hàng tuần | RPO 1 tuần |
| Cấu hình hạ tầng (docker-compose, nginx.conf, turnserver.conf) | Lưu trong Git (trừ secret) | Mỗi lần thay đổi | RPO 0 (luôn có trong version control) |
| Redis | Bật `appendonly yes` (AOF) — dữ liệu presence có thể mất mà không ảnh hưởng nghiêm trọng (tự phục hồi khi user reconnect) | Không cần backup nghiêm ngặt | Chấp nhận mất |

**Kịch bản khôi phục khi mất toàn bộ VPS**: dựng VPS mới → cài Docker → clone repo Git → restore `.env.prod` từ secret manager → restore DB từ bản backup gần nhất (`gunzip -c backup.sql.gz | docker exec -i chatsphere-postgres psql -U chatsphere_prod chatsphere`) → `docker compose up -d`.

---

## 15. CHI PHÍ ƯỚC TÍNH (THAM KHẢO, QUY MÔ DỰ ÁN CÁ NHÂN)

| Hạng mục | Chi phí ước tính/tháng |
|---|---|
| VPS 2vCPU/4GB (DigitalOcean/Hetzner) | 5-12 USD |
| Domain (.app/.com) | ~1 USD/tháng (tính theo năm) |
| Object storage (nếu dùng dịch vụ ngoài thay MinIO tự host) | 0-5 USD (free tier thường đủ cho demo) |
| SMTP (SendGrid/Mailgun free tier) | 0 USD (giới hạn số email/tháng) |
| TURN bandwidth (nếu tự host) | Đã bao gồm trong băng thông VPS, chú ý gói băng thông của nhà cung cấp VPS |
| **Tổng ước tính** | **~6-18 USD/tháng** |

> Ghi chú: mức chi phí này phù hợp cho mục đích demo/học tập/portfolio cá nhân, không phải sizing cho sản phẩm thương mại có lượng người dùng lớn.

---

## 16. CHECKLIST TRƯỚC KHI GO-LIVE

- [ ] Toàn bộ secret production khác hoàn toàn với dev (`JWT_SECRET`, `TURN_SECRET`, mật khẩu DB/Redis).
- [ ] `spring.jpa.hibernate.ddl-auto=validate` (không phải `update`/`create`).
- [ ] HTTPS hoạt động, chứng chỉ hợp lệ, HTTP tự động redirect sang HTTPS.
- [ ] WSS (WebSocket qua TLS) hoạt động — kiểm tra bằng DevTools Network tab thấy `wss://` không phải `ws://`.
- [ ] TURN server test thành công candidate loại `relay` (mục 8.3).
- [ ] Backup DB tự động đã chạy thử ít nhất 1 lần và khôi phục thử thành công (không chỉ tin backup tồn tại mà chưa test restore).
- [ ] Firewall chỉ mở đúng port cần thiết.
- [ ] Health check + alerting hoạt động (thử tắt 1 service để xác nhận có cảnh báo).
- [ ] CORS chỉ cho phép đúng domain frontend production.
- [ ] Rate limiting hoạt động trên endpoint đăng nhập/đăng ký.
- [ ] Test toàn bộ luồng chính trên môi trường production thật (đăng ký, chat, gọi video giữa 2 mạng khác nhau) trước khi công bố cho người dùng thật.
- [ ] Có kế hoạch rollback (biết cách quay lại version trước nếu deploy mới bị lỗi — ví dụ giữ lại docker image tag của bản trước).

---

*Hết tài liệu 04_PRODUCTION_DEPLOYMENT.md — tiếp theo xem `05_CLAUDE_CODE_SKILL.md` để cấu hình cách Claude Code hỗ trợ bạn code dự án này.*
