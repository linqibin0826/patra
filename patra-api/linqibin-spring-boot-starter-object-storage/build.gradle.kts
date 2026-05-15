/**
 * Patra Spring Boot Starter - Object Storage
 *
 * 对象存储抽象层：
 * - MinIO/S3 providers
 * - 重试机制
 * - 监控指标
 */

plugins {
    id("linqibin.spring-boot-starter")
}

dependencies {
    // Spring Boot Starter
    api("org.springframework.boot:spring-boot-starter")

    // Patra 内部依赖
    api(project(":patra-common:patra-common-storage"))

    // MinIO Client
    api(libs.minio)

    // AWS S3 SDK (optional)
    compileOnly(libs.aws.sdk.s3)

    // Spring Retry
    api("org.springframework.retry:spring-retry")

    // Micrometer
    api("io.micrometer:micrometer-core")

    // Configuration metadata
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
}
