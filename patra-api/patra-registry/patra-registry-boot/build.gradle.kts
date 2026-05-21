/**
 * Patra Registry Boot
 *
 * 启动层 - Spring Boot 应用入口
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-boot")
}

springBoot {
    mainClass = "dev.linqibin.patra.registry.PatraRegistryApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-registry:patra-registry-adapter"))
    implementation(project(":patra-registry:patra-registry-infra"))

    // Web Starter
    implementation(project(":linqibin-spring-boot-starter-web"))

    // 可观测性
    implementation(project(":linqibin-spring-boot-starter-observability"))

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
    testImplementation(libs.testcontainers.jdbc)
}
