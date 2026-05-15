/**
 * Patra Spring Boot Starter - Batch
 *
 * Spring Batch 批处理基础设施：
 * - 自动配置
 * - 可观测性集成
 * - 分布式锁支持
 */

plugins {
    id("linqibin.spring-boot-starter")
}

dependencies {
    // Patra 内部依赖
    api(project(":patra-common:patra-common-core"))
    api(project(":linqibin-spring-boot-starter-core"))
    api(project(":linqibin-spring-boot-starter-redisson"))

    // Spring Batch 核心
    api("org.springframework.boot:spring-boot-starter-batch")

    // Spring Boot 自动配置
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Spring Boot JDBC Starter（Spring Boot 4.0 模块化后需要显式引入）
    compileOnly("org.springframework.boot:spring-boot-starter-data-jdbc")

    // HikariCP 连接池（可选，用于独立 Batch 数据源）
    compileOnly("com.zaxxer:HikariCP")

    // MySQL JDBC 驱动（可选，用于 JobRepository 元数据存储）
    compileOnly("com.mysql:mysql-connector-j")

    // Micrometer Observation（可选，用于 Spring Batch 原生可观测性）
    compileOnly("io.micrometer:micrometer-observation")

    // Micrometer Core（可选，用于 BatchProgressMetricsListener）
    compileOnly("io.micrometer:micrometer-core")

    // 配置元数据
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
    testImplementation("org.springframework.batch:spring-batch-test")
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
