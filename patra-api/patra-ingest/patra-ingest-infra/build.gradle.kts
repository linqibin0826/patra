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
    api(project(":patra-api:patra-ingest:patra-ingest-domain"))
    api(project(":patra-api:patra-common"))
    api(project(":patra-tool:patra-tool-jpa"))
    api(project(":patra-api:patra-expr-kernel"))
    api(project(":patra-starters:patra-spring-boot-starter-expr"))
    api(project(":patra-tool:patra-tool-http-client"))
    api(project(":patra-starters:patra-spring-boot-starter-provenance"))
    api(project(":patra-api:patra-registry:patra-registry-api"))
    api(project(":patra-tool:patra-tool-object-storage"))
    api(project(":patra-api:patra-object-storage:patra-object-storage-api"))

    // RocketMQ
    api(libs.rocketmq.spring.boot)

    // MapStruct 由 patra.hexagonal-infra 插件提供
    // annotationProcessor 由 patra.java-base 插件提供

    // Spring Boot Starter
    api("org.springframework.boot:spring-boot-starter")

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
}
