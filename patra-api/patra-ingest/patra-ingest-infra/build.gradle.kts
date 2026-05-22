/**
 * Patra Ingest Infrastructure
 *
 * 基础设施层 - Repository 实现、外部服务集成
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-infra")
}

dependencies {
    // 内部模块
    api(project(":patra-ingest:patra-ingest-domain"))
    api(project(":patra-common:patra-common-model"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-jpa"))
    api(project(":patra-expr-kernel"))
    api(project(":patra-starters:patra-spring-boot-starter-expr"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-http-interface"))
    api(project(":patra-starters:patra-spring-boot-starter-provenance"))
    api(project(":patra-registry:patra-registry-api"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-object-storage"))
    api(project(":patra-object-storage:patra-object-storage-api"))

    // RocketMQ
    api(libs.rocketmq.spring.boot)

    // MapStruct 由 patra.hexagonal-infra 插件提供
    // annotationProcessor 由 patra.java-base 插件提供

    // Spring Boot Starter
    api("org.springframework.boot:spring-boot-starter")

    // 测试依赖
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
}
