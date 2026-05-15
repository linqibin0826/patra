/**
 * Patra Object Storage Infrastructure
 *
 * 基础设施层 - Repository 实现、外部服务集成
 */

plugins {
    id("linqibin.hexagonal-infra")
}

dependencies {
    // 内部模块
    api(project(":patra-object-storage:patra-object-storage-domain"))
    api(project(":patra-spring-boot-starter-jpa"))
    api(project(":patra-spring-boot-starter-core"))

    // Spring Boot Starter
    api("org.springframework.boot:spring-boot-starter")

    // MapStruct 由 patra.hexagonal-infra 插件提供
    // annotationProcessor 由 patra.java-base 插件提供

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
