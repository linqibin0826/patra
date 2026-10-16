/**
 * Patra Ingest Boot
 *
 * 启动层 - Spring Boot 应用入口
 */

plugins {
    id("patra.hexagonal-boot")
}

springBoot {
    mainClass = "dev.patra.ingest.PatraIngestApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-api:patra-ingest:patra-ingest-adapter"))
    implementation(project(":patra-api:patra-ingest:patra-ingest-infra"))
    implementation(project(":patra-api:patra-ingest:patra-ingest-api"))

    // Web Starter
    implementation(project(":patra-tool:patra-tool-web"))

    // HTTP Interface 客户端
    implementation(project(":patra-tool:patra-tool-http-client"))

    // 可观测性
    implementation(project(":patra-tool:patra-tool-observability"))

    // Object Storage
    implementation(project(":patra-tool:patra-tool-object-storage"))
    implementation(project(":patra-api:patra-object-storage:patra-object-storage-api"))

    // RocketMQ
    implementation(libs.rocketmq.spring.boot) {
        exclude(group = "commons-logging", module = "commons-logging")
    }

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))

    // JMH for performance benchmarks
    testImplementation(libs.jmh.core)
    testAnnotationProcessor(libs.jmh.generator)
}
