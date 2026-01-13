/**
 * Patra Spring Boot Starter - REST Client
 *
 * REST 客户端基础设施：
 * - RestClient 配置
 * - 重试支持
 * - 下载进度监控
 */

plugins {
    id("patra.spring-boot-starter")
}

dependencies {
    // Patra 内部依赖
    api(project(":patra-common:patra-common-core"))
    api(project(":patra-spring-boot-starter-core"))

    // Spring Boot AutoConfiguration support
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Spring Web: provides RestClient
    api("org.springframework:spring-web")

    // Spring Retry: optional retry support
    compileOnly("org.springframework.retry:spring-retry")

    // Micrometer: optional metrics support for download progress
    compileOnly("io.micrometer:micrometer-core")

    // WebFlux: optional WebClient support for streaming downloads
    compileOnly("org.springframework:spring-webflux")

    // Reactor Netty: WebClient underlying HTTP client
    compileOnly("io.projectreactor.netty:reactor-netty-http")

    // Apache Commons Net: FTP client for LSIOU data source
    api("commons-net:commons-net:3.11.1")

    // Configuration metadata
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
