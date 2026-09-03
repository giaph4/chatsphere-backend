package com.chatsphere.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

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
}