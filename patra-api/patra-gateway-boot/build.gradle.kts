/**
 * Patra API Gateway Boot
 *
 * API 网关 - Spring Cloud Gateway
 */

plugins {
    id("linqibin.hexagonal-boot")
}

springBoot {
    mainClass = "com.patra.gateway.PatraGatewayApplication"
}

dependencies {
    // Patra Starter
    implementation(project(":patra-spring-boot-starter-core"))
    implementation(project(":patra-spring-boot-starter-observability"))

    // Spring Cloud Gateway (WebFlux)
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")

    // LoadBalancer
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")

    // API 文档聚合（WebFlux 版本 Scalar UI）
    implementation(libs.springdoc.openapi.webflux.scalar)

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
