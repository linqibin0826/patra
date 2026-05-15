/**
 * Patra Ingest Boot
 *
 * 启动层 - Spring Boot 应用入口
 */

plugins {
    id("linqibin.hexagonal-boot")
}

springBoot {
    mainClass = "dev.linqibin.patra.ingest.PatraIngestApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-ingest:patra-ingest-adapter"))
    implementation(project(":patra-ingest:patra-ingest-infra"))

    // Web Starter
    implementation(project(":linqibin-spring-boot-starter-web"))

    // HTTP Interface 客户端
    implementation(project(":linqibin-spring-boot-starter-http-interface"))

    // 可观测性
    implementation(project(":linqibin-spring-boot-starter-observability"))

    // Object Storage
    implementation(project(":linqibin-spring-boot-starter-object-storage"))
    implementation(project(":patra-object-storage:patra-object-storage-api"))

    // RocketMQ
    implementation(libs.rocketmq.spring.boot) {
        exclude(group = "commons-logging", module = "commons-logging")
    }

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))

    // JMH for performance benchmarks
    testImplementation(libs.jmh.core)
    testAnnotationProcessor(libs.jmh.generator)
}
