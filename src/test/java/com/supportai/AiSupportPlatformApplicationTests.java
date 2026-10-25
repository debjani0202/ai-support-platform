package com.supportai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/*
 * @Test comes from JUnit 5. It marks a method as a test
 * that the testing framework should run.
 *
 * @SpringBootTest tells Spring Boot to create an application test context.
 */

@SpringBootTest
@ActiveProfiles("test")
class AiSupportPlatformApplicationTests {

    @Test
    void contextLoads() {
    }
}