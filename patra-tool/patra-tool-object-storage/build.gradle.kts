/**
 * Patra Spring Boot Starter - Object Storage
 *
 * 对象存储抽象层：
 * - MinIO/S3 providers
 * - 重试机制
 * - 监控指标
 */

plugins {
    id("patra.tool-module")
    id("patra.spring-starter")
}

dependencies {
    // Spring Boot Starter
    api("org.springframework.boot:spring-boot-starter")

    // MinIO Client
    api(libs.minio)

    // AWS S3 SDK (optional)
    compileOnly(libs.aws.sdk.s3)

    // Spring Retry
    api("org.springframework.retry:spring-retry")

    // Micrometer
    api("io.micrometer:micrometer-core")

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
    // S3 契约测试要能看到 S3Client
    testImplementation(libs.aws.sdk.s3)
}
