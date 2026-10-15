/**
 * Patra Ingest Application
 *
 * 应用层 - 用例编排、事务边界
 */

plugins {
    id("patra.hexagonal-app")
}

dependencies {
    // 内部模块
    api(project(":patra-api:patra-common"))
    api(project(":patra-tool:patra-tool-core"))
    api(project(":patra-tool:patra-tool-cqrs"))
    api(project(":patra-api:patra-ingest:patra-ingest-domain"))
    api(project(":patra-api:patra-ingest:patra-ingest-api"))
    api(project(":patra-starters:patra-spring-boot-starter-expr"))
    api(project(":patra-api:patra-expr-kernel"))
    api(project(":patra-starters:patra-spring-boot-starter-provenance"))

    // Spring 依赖
    api("org.springframework:spring-tx")
    api("org.springframework.boot:spring-boot-starter-aspectj")

    // Micrometer
    api("io.micrometer:micrometer-core")

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
}
