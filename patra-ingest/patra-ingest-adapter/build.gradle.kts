/**
 * Patra Ingest Adapter
 *
 * 适配器层 - Controller、Job、消息处理器
 */

plugins {
    id("linqibin.hexagonal-adapter")
}

dependencies {
    // 内部模块
    api(project(":patra-ingest:patra-ingest-app"))
    api(project(":patra-ingest:patra-ingest-api"))
    api(project(":patra-spring-boot-starter-web"))

    // RocketMQ
    api(libs.rocketmq.spring.boot)

    // MapStruct 由 patra.hexagonal-adapter 插件提供
    // annotationProcessor 由 patra.java-base 插件提供

    // XXL-Job
    api(libs.xxl.job)

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
