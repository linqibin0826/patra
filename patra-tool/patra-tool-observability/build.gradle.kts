/**
 * Patra Spring Boot Starter - Observability
 *
 * 可观测性基础设施：
 * - Metrics (Micrometer)
 * - Tracing (OTel Agent)
 * - Logging (MDC trace_id/span_id)
 */

plugins {
    id("patra.tool-module")
    id("patra.spring-starter")
}

dependencies {
    // Patra 内部依赖
    api(project(":patra-tool:patra-tool-core"))
    compileOnly(project(":patra-tool:patra-tool-web"))
    // 契约测试要能看到 web 的 ResolutionInterceptor
    testImplementation(project(":patra-tool:patra-tool-web"))

    // Foundation for custom AutoConfiguration
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Micrometer Observation API (核心)
    api("io.micrometer:micrometer-observation")

    // Micrometer Core (Metrics)
    api("io.micrometer:micrometer-core")

    // TraceIdConverter / SpanIdConverter 直接继承 logback 的转换器
    implementation("ch.qos.logback:logback-classic")

    // Spring Boot Actuator: 提供 WebMvcObservationAutoConfiguration 等观测相关自动配置
    api("org.springframework.boot:spring-boot-starter-actuator")

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
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
