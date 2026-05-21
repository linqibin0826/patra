/**
 * Patra Catalog Boot
 *
 * 启动层 - Spring Boot 应用入口
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-boot")
}

springBoot {
    mainClass = "dev.linqibin.patra.catalog.PatraCatalogApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-catalog:patra-catalog-adapter"))
    implementation(project(":patra-catalog:patra-catalog-infra"))

    // Web Starter
    implementation(project(":linqibin-spring-boot-starter-web"))

    // HTTP Interface 客户端
    implementation(project(":linqibin-spring-boot-starter-http-interface"))

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 可观测性
    implementation(project(":linqibin-spring-boot-starter-observability"))

    // 对象存储
    implementation(project(":linqibin-spring-boot-starter-object-storage"))

    // API 文档
    implementation(project(":linqibin-spring-boot-starter-openapi"))

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
}
