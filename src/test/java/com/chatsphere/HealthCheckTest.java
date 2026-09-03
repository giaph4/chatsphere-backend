package com.chatsphere;

import com.chatsphere.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test đầu tiên: xác nhận toàn bộ bộ khung (context + Postgres container + Flyway + web) khởi động OK.
 */
class HealthCheckTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void healthEndpoint_reportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}