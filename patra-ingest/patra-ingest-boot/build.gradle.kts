/**
 * Patra Ingest Boot
 *
 * 启动层 - Spring Boot 应用入口
 */

plugins {
    id("patra.hexagonal-boot")
}

springBoot {
    mainClass = "com.patra.ingest.PatraIngestApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-ingest:patra-ingest-adapter"))
    implementation(project(":patra-ingest:patra-ingest-infra"))

    // Web Starter
    implementation(project(":patra-spring-boot-starter-web"))

    // HTTP Interface 客户端
    implementation(project(":patra-spring-boot-starter-http-interface"))

    // 可观测性
    implementation(project(":patra-spring-boot-starter-observability"))

    // Object Storage
    implementation(project(":patra-spring-boot-starter-object-storage"))
    implementation(project(":patra-object-storage:patra-object-storage-api"))

    // RocketMQ
    implementation("org.apache.rocketmq:rocketmq-spring-boot-starter:2.3.1") {
        exclude(group = "commons-logging", module = "commons-logging")
    }

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))

    // JMH for performance benchmarks
    testImplementation("org.openjdk.jmh:jmh-core:1.37")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}
