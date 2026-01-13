/**
 * Patra Catalog API
 *
 * 服务契约层 - DTO、事件、接口定义
 */

plugins {
    id("patra.hexagonal-api")
}

dependencies {
    api(project(":patra-common:patra-common-core"))

    // DTO 验证注解
    api("jakarta.validation:jakarta.validation-api")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
