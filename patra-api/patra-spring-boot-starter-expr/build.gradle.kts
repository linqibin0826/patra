/**
 * Patra Spring Boot Starter - Expression
 *
 * Expression 引擎 Spring Boot 自动配置
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.spring-boot-starter")
}

dependencies {
    // 内部模块
    api(project(":patra-expr-kernel"))
    api(project(":patra-common:patra-common-enums"))
    api(project(":linqibin-commons-core"))
    api(project(":linqibin-spring-boot-starter-core"))
    api(project(":patra-registry:patra-registry-api"))

    // HTTP Interface（可选）
    compileOnly(project(":linqibin-spring-boot-starter-http-interface"))

    // Micrometer（可选）
    compileOnly("io.micrometer:micrometer-core")

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
}
