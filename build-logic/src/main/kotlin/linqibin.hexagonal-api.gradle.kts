/**
 * Patra Hexagonal API Layer Convention Plugin
 *
 * API 层 - 服务契约定义
 *
 * 职责：
 * - DTO (Data Transfer Objects)
 * - 错误码定义
 * - 服务接口定义 (用于 HTTP Interface)
 * - 无业务逻辑
 */

plugins {
    id("linqibin.java-library")
}

dependencies {
    // API 层核心依赖
    api(project(":patra-common:patra-common-enums"))
    api(project(":linqibin-commons:linqibin-commons-core"))

    // Jakarta Validation (仅用于 DTO 校验注解)
    compileOnly("jakarta.validation:jakarta.validation-api")

    // Spring Web (仅用于 @RequestParam 等注解，用于 HTTP Interface)
    compileOnly("org.springframework:spring-web")
}

// API 模块通常没有测试（纯数据结构）
tasks.test {
    enabled = false
}

tasks.jacocoTestReport {
    enabled = false
}
