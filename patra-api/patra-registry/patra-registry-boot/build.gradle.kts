/**
 * Patra Registry Boot
 *
 * 启动层 - Spring Boot 应用入口
 */

plugins {
    id("patra.hexagonal-boot")
}

springBoot {
    mainClass = "dev.patra.registry.PatraRegistryApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-api:patra-registry:patra-registry-adapter"))
    implementation(project(":patra-api:patra-registry:patra-registry-infra"))

    // Web Starter
    implementation(project(":patra-tool:patra-tool-web"))

    // 可观测性
    implementation(project(":patra-tool:patra-tool-observability"))

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
    testImplementation(libs.testcontainers.jdbc)
}
