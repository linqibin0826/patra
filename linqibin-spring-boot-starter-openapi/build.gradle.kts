/**
 * Patra Spring Boot Starter - OpenAPI
 *
 * API 文档自动配置：
 * - SpringDoc OpenAPI 3.0.1 + Scalar UI
 * - Javadoc 运行时读取（配合 adapter 层的 therapi-javadoc-scribe 注解处理器）
 */

plugins {
    id("linqibin.spring-boot-starter")
}

dependencies {
    // SpringDoc OpenAPI + Scalar UI
    api(libs.springdoc.openapi.scalar)

    // Therapi Javadoc 运行时读取库（编译期 scribe 在 adapter convention plugin 中声明）
    api(libs.therapi.javadoc)

    // Spring Web（条件判断用）
    compileOnly("org.springframework:spring-web")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
