/**
 * Patra Object Storage Infrastructure
 *
 * 基础设施层 - Repository 实现、外部服务集成
 */

plugins {
    id("patra.hexagonal-infra")
}

dependencies {
    // 内部模块
    api(project(":patra-object-storage:patra-object-storage-domain"))
    api(project(":patra-spring-boot-starter-jpa"))
    api(project(":patra-spring-boot-starter-core"))

    // Spring Boot Starter
    api("org.springframework.boot:spring-boot-starter")

    // MapStruct
    api("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
