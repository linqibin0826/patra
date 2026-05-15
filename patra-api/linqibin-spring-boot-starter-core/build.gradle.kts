/**
 * Patra Spring Boot Starter - Core
 *
 * 核心 Starter，提供：
 * - JSON 序列化配置
 * - 错误处理管道
 * - 异步执行器配置
 */

plugins {
    id("linqibin.spring-boot-starter")
}

dependencies {
    // Internal shared library: utilities/exceptions
    api(project(":patra-common-enums"))
    api(project(":linqibin-commons-core"))

    // Foundation for custom AutoConfiguration
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Official Boot JSON starter (jackson-databind + common modules)
    api("org.springframework.boot:spring-boot-starter-json")

    // Jackson 3 XML (optional): enables XmlMapper auto-config when present
    compileOnly("tools.jackson.dataformat:jackson-dataformat-xml")

    // Resilience4j circuit breaker (optional: only needed if error-pipeline circuit breaker is enabled)
    compileOnly("io.github.resilience4j:resilience4j-circuitbreaker")

    // Micrometer (optional: for async executor metrics)
    compileOnly("io.micrometer:micrometer-core")

    // Configuration metadata (IDE hints during development; not used at runtime)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
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
