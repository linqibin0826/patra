/**
 * Patra Ingest Application
 *
 * 应用层 - 用例编排、事务边界
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-app")
}

dependencies {
    // 内部模块
    api(project(":patra-common:patra-common-enums"))
    api(project(":linqibin-commons-core"))
    api(project(":patra-ingest:patra-ingest-domain"))
    api(project(":patra-ingest:patra-ingest-api"))
    api(project(":linqibin-spring-boot-starter-core"))
    api(project(":patra-spring-boot-starter-expr"))
    api(project(":patra-expr-kernel"))
    api(project(":patra-spring-boot-starter-provenance"))

    // Spring 依赖
    api("org.springframework:spring-tx")
    api("org.springframework.boot:spring-boot-starter-aspectj")

    // Micrometer
    api("io.micrometer:micrometer-core")

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
}
