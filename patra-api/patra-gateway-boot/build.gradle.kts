/**
 * Patra API Gateway Boot
 *
 * API 网关 - Spring Cloud Gateway（WebMVC 版，servlet 栈）+ Spring Security 鉴权
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

    // 鉴权：Spring Security、无状态默认配置、ProblemDetail 写出器、验签器、签名器、认证对象
    implementation(project(":patra-starters:patra-spring-boot-starter-security"))

    // 会话存储契约（RedisSessionStore、SessionToken）；它用 api 带进 spring-boot-starter-data-redis
    implementation(project(":patra-api:patra-identity:patra-identity-session"))

    // 测试依赖（含 RestTestClient 与 WireMock）
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))

    // 测试里的签名密钥、自动注入测试公钥的环境后处理器、spring-boot-security-test
    testImplementation(testFixtures(project(":patra-starters:patra-spring-boot-starter-security")))
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
