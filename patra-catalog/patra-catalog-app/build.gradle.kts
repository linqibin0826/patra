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
    api(project(":patra-common:patra-common-core"))
    api(project(":patra-catalog:patra-catalog-domain"))
    api(project(":patra-catalog:patra-catalog-api"))
    api(project(":patra-spring-boot-starter-core"))
    api(project(":patra-spring-boot-starter-batch"))

    // Spring 依赖
    api("org.springframework:spring-tx")
    api("org.springframework.boot:spring-boot-starter-aspectj")

    // Micrometer
    api("io.micrometer:micrometer-core")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
