/**
 * Patra API Gateway Boot
 *
 * API 网关 - Spring Cloud Gateway（WebMVC 版，servlet 栈）
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-boot")
}

springBoot {
    mainClass = "dev.linqibin.patra.gateway.PatraGatewayApplication"
}

dependencies {
    // Patra Starter：错误引擎、ProblemDetail 全局处理器、可观测性
    implementation(project(":linqibin-commons:linqibin-spring-boot-starter-core"))
    implementation(project(":linqibin-commons:linqibin-spring-boot-starter-web"))
    implementation(project(":linqibin-commons:linqibin-spring-boot-starter-observability"))

    // Spring Cloud Gateway（WebMVC 版）：代理走 Boot 的 RestClient，HTTP 客户端由 spring.http.clients.* 决定
    implementation(libs.spring.cloud.starter.gateway)

    // LoadBalancer：lb:// 路由
    implementation(libs.spring.cloud.starter.loadbalancer)

    // API 文档聚合（WebMVC 版 Scalar UI）
    implementation(libs.springdoc.openapi.scalar)

    // 测试依赖（含 RestTestClient 与 WireMock）
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
}

// 网关没有数据库：测试 starter（hexagonal-boot 插件也会加它）带着 JPA / JDBC / Flyway 的测试模块，
// 会把 DataSource 自动配置带进测试上下文、让它起不来。在配置级别排除，integrationTest 继承 test 的配置。
configurations.testImplementation {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-data-jpa-test")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-jdbc-test")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-flyway-test")
    exclude(group = "org.flywaydb", module = "flyway-database-postgresql")
    exclude(group = "org.postgresql", module = "postgresql")
}
