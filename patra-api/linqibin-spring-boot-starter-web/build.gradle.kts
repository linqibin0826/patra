/**
 * Patra Spring Boot Starter - Web
 *
 * Web 层基础设施：
 * - Spring MVC 配置
 * - Jackson 序列化
 * - 参数验证
 */

plugins {
    id("linqibin.spring-boot-starter")
}

dependencies {
    // Patra 内部依赖
    api(project(":patra-common-enums"))
    api(project(":linqibin-commons-core"))
    api(project(":linqibin-spring-boot-starter-core"))

    // Spring Boot Web MVC
    api("org.springframework.boot:spring-boot-starter-webmvc")

    // Spring Boot 4.0 模块化后，Jackson 需要显式依赖
    api("org.springframework.boot:spring-boot-starter-jackson")

    // 参数验证
    api("org.springframework.boot:spring-boot-starter-validation")

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
}
