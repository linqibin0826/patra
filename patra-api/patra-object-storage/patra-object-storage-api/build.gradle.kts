/**
 * Patra Object Storage API
 *
 * 服务契约层 - DTO、接口定义
 */

plugins {
    id("linqibin.hexagonal-api")
}

dependencies {
    api(project(":patra-common:patra-common-enums"))
    api(project(":linqibin-commons-core"))

    // DTO 验证注解
    api("jakarta.validation:jakarta.validation-api")

    // @HttpExchange 注解（provided scope）
    compileOnly("org.springframework:spring-web")
}

// API 模块没有测试代码，跳过测试执行
tasks.test {
    enabled = false
}
