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
    api(project(":patra-api:patra-common:patra-common-enums"))
    api(project(":linqibin-commons:linqibin-commons-core"))
    api(project(":patra-api:patra-ingest:patra-ingest-domain"))
    api(project(":patra-api:patra-ingest:patra-ingest-api"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-core"))
    api(project(":patra-starters:patra-spring-boot-starter-expr"))
    api(project(":patra-api:patra-expr-kernel"))
    api(project(":patra-starters:patra-spring-boot-starter-provenance"))

    // Spring 依赖
    api("org.springframework:spring-tx")
    api("org.springframework.boot:spring-boot-starter-aspectj")

    // Micrometer
    api("io.micrometer:micrometer-core")

    // 测试依赖
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
}
