package com.patra.egress;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Application startup test
 *
 * @author linqibin
 * @since 0.1.0
 */
@SpringBootTest
@ActiveProfiles("test")
class EgressGatewayApplicationTest {

    @Test
    void contextLoads() {
        // Verify that the application context loads successfully
    }
}
