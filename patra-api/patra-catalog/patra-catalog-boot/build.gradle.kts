/**
 * Patra Catalog Boot
 *
 * 启动层 - Spring Boot 应用入口
 */

plugins {
    id("patra.hexagonal-boot")
}

springBoot {
    mainClass = "dev.patra.catalog.PatraCatalogApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-api:patra-catalog:patra-catalog-adapter"))
    implementation(project(":patra-api:patra-catalog:patra-catalog-infra"))

    // Web Starter
    implementation(project(":patra-tool:patra-tool-web"))

    // HTTP Interface 客户端
    implementation(project(":patra-tool:patra-tool-http-client"))

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 可观测性
    implementation(project(":patra-tool:patra-tool-observability"))

    // 对象存储
    implementation(project(":patra-tool:patra-tool-object-storage"))

    // API 文档
    implementation(project(":patra-tool:patra-tool-openapi"))

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
}
