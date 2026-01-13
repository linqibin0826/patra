/**
 * Patra Spring Boot Starter - HTTP Interface
 *
 * Spring Boot 4.0 HTTP Interface (@HttpExchange) 支持：
 * - 替代 Feign 的现代声明式 HTTP 客户端
 * - 服务发现集成
 * - 统一错误处理
 */

plugins {
    id("patra.spring-boot-starter")
}

dependencies {
    // Patra 通用核心（提供 RemoteCallException、ErrorTrait 等错误处理基础设施）
    api(project(":patra-common:patra-common-core"))

    // Spring Boot RestClient（提供 RestClientCustomizer 等）
    api("org.springframework.boot:spring-boot-restclient")

    // Spring Boot AutoConfiguration
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Spring Web（提供 RestClient、@HttpExchange 等）
    api("org.springframework:spring-web")

    // Spring Cloud LoadBalancer（服务发现支持）
    api("org.springframework.cloud:spring-cloud-starter-loadbalancer")

    // Micrometer（可观测性支持）
    api("io.micrometer:micrometer-core")

    // Configuration metadata
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
