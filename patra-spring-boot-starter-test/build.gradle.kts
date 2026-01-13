/**
 * Patra Spring Boot Starter - Testing
 *
 * 统一测试基础设施：
 * - TestContainers 容器工厂
 * - ArchUnit 架构规则
 * - 测试基类和工具
 */

plugins {
    id("patra.spring-boot-starter")
}

dependencies {
    // ==================== 核心测试框架（api scope，暴露给使用方）====================

    // JUnit 5
    api("org.junit.jupiter:junit-jupiter:5.11.4")

    // AssertJ 流式断言
    api("org.assertj:assertj-core:3.27.3")

    // Mockito Mock 框架
    api("org.mockito:mockito-core:5.15.2")
    api("org.mockito:mockito-junit-jupiter:5.15.2")

    // ==================== Spring Boot Test ====================

    api("org.springframework.boot:spring-boot-starter-test") {
        // 排除重复的测试框架，使用我们统一管理的版本
        exclude(group = "org.junit.jupiter", module = "junit-jupiter")
        exclude(group = "org.mockito", module = "mockito-core")
        exclude(group = "org.mockito", module = "mockito-junit-jupiter")
        exclude(group = "org.assertj", module = "assertj-core")
    }

    // Spring Boot 4.0 模块化测试支持
    api("org.springframework.boot:spring-boot-starter-data-jpa-test")
    api("org.springframework.boot:spring-boot-starter-jdbc-test")
    api("org.springframework.boot:spring-boot-starter-flyway-test")
    api("org.springframework.boot:spring-boot-starter-webmvc-test")
    api("org.springframework.boot:spring-boot-starter-restclient-test")

    // Spring Boot AutoConfiguration 支持
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Spring Transaction（ArchUnit 规则需要 @Transactional 注解）
    api("org.springframework:spring-tx")

    // Configuration Processor（IDE 提示支持）
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // ==================== TestContainers ====================

    api("org.testcontainers:testcontainers")
    api("org.testcontainers:junit-jupiter")
    api("org.testcontainers:mysql")
    api("org.testcontainers:minio")

    // MinIO Java Client（MinIOContainerInitializer 创建存储桶需要）
    api("io.minio:minio:8.5.7") {
        // 排除 Kotlin 传递的旧版 annotations
        exclude(group = "org.jetbrains", module = "annotations")
    }

    // ==================== ArchUnit 架构测试 ====================

    api("com.tngtech.archunit:archunit-junit5:1.3.0")

    // ==================== 其他测试工具 ====================

    // WireMock HTTP Mock Server
    api("org.wiremock:wiremock-standalone:3.10.0")

    // Awaitility 异步测试工具
    api("org.awaitility:awaitility:4.2.2")

    // ==================== Micrometer（SimpleMeterRegistry 支持）====================

    api("io.micrometer:micrometer-core")

    // ==================== 数据库驱动（TestContainers MySQL 需要）====================

    api("com.mysql:mysql-connector-j")

    // ==================== SLF4J 日志 ====================

    api("org.slf4j:slf4j-api")

    // Jackson（JSON 序列化支持）
    compileOnly("tools.jackson.core:jackson-databind")
}

// 本模块暂不启用 JaCoCo 覆盖率检查（测试基础设施模块）
tasks.jacocoTestCoverageVerification {
    enabled = false
}
