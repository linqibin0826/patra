/**
 * Patra Spring Boot Starter - Core
 *
 * 核心 Starter，提供：
 * - CQRS 命令总线
 * - 错误处理管道
 * - 异步执行器配置
 */

plugins {
    id("patra.boundary-check")
    id("patra.spring-starter")
}

dependencies {
    // Internal shared library: utilities/exceptions
    api(project(":patra-tool:patra-tool-core"))

    // Foundation for custom AutoConfiguration
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Official Boot JSON starter (jackson-databind + common modules)
    api("org.springframework.boot:spring-boot-starter-json")

    // Resilience4j circuit breaker (optional: only needed if error-pipeline circuit breaker is enabled)
    compileOnly("io.github.resilience4j:resilience4j-circuitbreaker")

    // Micrometer (optional: for async executor metrics)
    compileOnly("io.micrometer:micrometer-core")

    // Configuration metadata (IDE hints during development; not used at runtime)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
}

// 覆盖率要求 75%
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}
