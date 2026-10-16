/**
 * Patra Catalog Application
 *
 * 应用层 - 用例编排、事务边界
 */

plugins {
    id("patra.hexagonal-app")
}

dependencies {
    // 内部模块
    api(project(":patra-tool:patra-tool-core"))
    api(project(":patra-tool:patra-tool-cqrs"))
    api(project(":patra-api:patra-catalog:patra-catalog-domain"))
    api(project(":patra-api:patra-catalog:patra-catalog-api"))
    api(project(":patra-tool:patra-tool-batch"))

    // Spring 依赖
    api("org.springframework:spring-tx")
    api("org.springframework.boot:spring-boot-starter-aspectj")

    // Micrometer
    api("io.micrometer:micrometer-core")

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
}
