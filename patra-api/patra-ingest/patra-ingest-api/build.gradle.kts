/**
 * Patra Ingest API
 *
 * 服务契约层 - DTO、事件、接口定义
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-api")
}

dependencies {
    api(project(":patra-api:patra-common:patra-common-enums"))
    api(project(":linqibin-commons:linqibin-commons-core"))

    // DTO 验证注解
    api("jakarta.validation:jakarta.validation-api")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}

// API 模块没有测试代码，跳过测试执行
tasks.test {
    enabled = false
}
