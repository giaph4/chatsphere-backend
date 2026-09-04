package com.chatsphere.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Khai các container dùng cho integration test.
 * {@code @ServiceConnection}: Spring Boot tự đọc jdbcUrl/username/password của container
 * và cấu hình DataSource — không cần @DynamicPropertySource thủ công.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        // image trùng với infra/docker-compose.yml -> đã pull sẵn, bật nhanh
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    /**
     * Redis không có container class chuyên dụng trong core Testcontainers → dùng GenericContainer.
     * name = "redis" để Boot biết map sang spring.data.redis.* (không suy ra được từ kiểu bean).
     * Container này KHÔNG đặt password → application-test.yaml phải ghi đè password rỗng.
     */
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
    }
}