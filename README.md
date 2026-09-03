# ChatSphere — Backend

Realtime chat + WebRTC video call. Spring Boot 4 (Java 21), PostgreSQL, Redis, MinIO, coturn.

## Tài liệu

| File | Nội dung |
|---|---|
| `01_SYSTEM_DESIGN.md` | Kiến trúc, thiết kế DB/API, package structure |
| `02_SETUP_GUIDE.md` | Cài đặt môi trường phát triển (file này mô tả trạng thái đã setup) |
| `03_CODE_ROADMAP.md` | Lộ trình code 9 Phase |
| `04_PRODUCTION_DEPLOYMENT.md` | Triển khai production |
| `05_CLAUDE_CODE_SKILL.md` | Đặc tả skill `che-do-lam-viec` cho Claude Code |

## Chạy nhanh (dev)

```bash
# 1. Hạ tầng: Postgres, Redis, MinIO, coturn, MailHog
cd infra && docker compose up -d && cd ..

# 2. Backend (profile dev — đã có sẵn giá trị mặc định khớp docker-compose)
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

- Health:      http://localhost:8080/actuator/health
- Swagger UI:   http://localhost:8080/swagger-ui.html
- MailHog:      http://localhost:8025
- MinIO Console:http://localhost:9001  (chatsphere_admin / minio_dev_password)

Muốn override cấu hình: copy `.env.example` → `.env` và chỉnh. `.env` không commit.

## Cấu trúc

```
src/main/java/com/chatsphere/
  ├── auth  user  chat  signaling  presence  notification  media   (module nghiệp vụ)
  ├── common                                                       (BaseEntity, ApiResponse, exception...)
  └── config                                                       (Security, WebSocket, Redis, CORS...)
src/main/resources/
  ├── application.yaml  application-dev.yaml  application-prod.yaml
  └── db/migration/                                                (Flyway V1__*.sql ...)
infra/
  ├── docker-compose.yml
  └── coturn/turnserver.conf
```
