/**
 * Patra Catalog Application
 *
 * 应用层 - 用例编排、事务边界
 */

plugins {
    id("linqibin.hexagonal-app")
}

dependencies {
    // 内部模块
    api(project(":patra-common-enums"))
    api(project(":linqibin-commons-core"))
    api(project(":patra-catalog:patra-catalog-domain"))
    api(project(":patra-catalog:patra-catalog-api"))
    api(project(":linqibin-spring-boot-starter-core"))
    api(project(":linqibin-spring-boot-starter-batch"))

    // Spring 依赖
    api("org.springframework:spring-tx")
    api("org.springframework.boot:spring-boot-starter-aspectj")

    // Micrometer
    api("io.micrometer:micrometer-core")

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
}
