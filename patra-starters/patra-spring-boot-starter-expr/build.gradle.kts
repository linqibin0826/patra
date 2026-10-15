/**
 * Patra Spring Boot Starter - Expression
 *
 * Expression 引擎 Spring Boot 自动配置
 */

plugins {
    id("patra.spring-starter")
}

dependencies {
    // 内部模块
    api(project(":patra-api:patra-expr-kernel"))
    api(project(":patra-api:patra-common:patra-common-enums"))
    api(project(":patra-tool:patra-tool-core"))
    api(project(":patra-api:patra-registry:patra-registry-api"))

    // Spring Boot 自动配置与日志（原先经 http-interface 的 compileOnly 间接拿到）
    api("org.springframework.boot:spring-boot-starter")

    // Micrometer（可选）
    compileOnly("io.micrometer:micrometer-core")

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
}
