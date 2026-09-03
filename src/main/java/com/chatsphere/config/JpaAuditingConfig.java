package com.chatsphere.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kích hoạt Spring Data JPA Auditing để {@code @CreatedDate}/{@code @LastModifiedDate}
 * trong {@link com.chatsphere.common.BaseEntity} được tự điền.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

}
