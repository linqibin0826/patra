/**
 * Patra Spring Boot Starter - Observability
 *
 * 可观测性基础设施：
 * - Metrics (Micrometer)
 * - Tracing (OTel Agent)
 * - Logging (MDC trace_id/span_id)
 */

plugins {
    id("linqibin.spring-boot-starter")
}

dependencies {
    // Patra 内部依赖
    api(project(":patra-common-enums"))
    api(project(":linqibin-commons-core"))
    api(project(":linqibin-spring-boot-starter-core"))

    // Foundation for custom AutoConfiguration
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Micrometer Observation API (核心)
    api("io.micrometer:micrometer-observation")

    // Micrometer Core (Metrics)
    api("io.micrometer:micrometer-core")

    // Caffeine Cache: 用于状态管理，避免内存泄漏
    api("com.github.ben-manes.caffeine:caffeine")

    // Jakarta Validation API: 用于配置属性验证
    api("jakarta.validation:jakarta.validation-api")

    // Jakarta Servlet API (可选): 用于 HTTP 可观测性 Filter 配置
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    // Spring Boot Actuator: 提供 WebMvcObservationAutoConfiguration 等观测相关自动配置
    api("org.springframework.boot:spring-boot-starter-actuator")

    // Configuration metadata
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    testImplementation("org.springframework.boot:spring-boot-starter-jackson")
    testImplementation("io.micrometer:micrometer-observation-test")
}

// 覆盖率要求 80%
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
