/**
 * Patra Registry Boot
 *
 * 启动层 - Spring Boot 应用入口
 */

plugins {
    id("linqibin.hexagonal-boot")
}

springBoot {
    mainClass = "com.patra.registry.PatraRegistryApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-registry:patra-registry-adapter"))
    implementation(project(":patra-registry:patra-registry-infra"))

    // Web Starter
    implementation(project(":patra-spring-boot-starter-web"))

    // 可观测性
    implementation(project(":patra-spring-boot-starter-observability"))

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
    testImplementation(libs.testcontainers.jdbc)
}
