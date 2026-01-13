/**
 * Patra Ingest Infrastructure
 *
 * 基础设施层 - Repository 实现、外部服务集成
 */

plugins {
    id("patra.hexagonal-infra")
}

dependencies {
    // 内部模块
    api(project(":patra-ingest:patra-ingest-domain"))
    api(project(":patra-common:patra-common-model"))
    api(project(":patra-spring-boot-starter-jpa"))
    api(project(":patra-expr-kernel"))
    api(project(":patra-spring-boot-starter-expr"))
    api(project(":patra-spring-boot-starter-http-interface"))
    api(project(":patra-spring-boot-starter-provenance"))
    api(project(":patra-registry:patra-registry-api"))
    api(project(":patra-spring-boot-starter-object-storage"))
    api(project(":patra-object-storage:patra-object-storage-api"))

    // RocketMQ
    api("org.apache.rocketmq:rocketmq-spring-boot-starter:2.3.1")

    // MapStruct
    api("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // Spring Boot Starter
    api("org.springframework.boot:spring-boot-starter")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
