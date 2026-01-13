/**
 * Patra Spring Boot Starter - Redisson
 *
 * 分布式锁和 Redis 基础设施：
 * - 基于 Redisson 实现
 * - 分布式锁注解支持
 */

plugins {
    id("patra.spring-boot-starter")
}

dependencies {
    // Patra 内部依赖
    api(project(":patra-common:patra-common-core"))
    api(project(":patra-spring-boot-starter-core"))

    // Redisson 官方 Starter
    api(libs.redisson.spring.boot)

    // Spring Boot 依赖
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-starter-aspectj")

    // 配置元数据
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
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
