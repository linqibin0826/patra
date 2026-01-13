/**
 * Patra Hexagonal Boot Layer Convention Plugin
 *
 * 启动层 - Spring Boot 应用入口
 *
 * 职责：
 * - @SpringBootApplication 入口
 * - 配置装配
 * - 生成可执行 fat JAR
 *
 * 注意：org.springframework.boot 插件会自动应用 io.spring.dependency-management，
 * 但 patra.java-base 中的 BOM 配置不会自动继承。这里通过 applyPatraDependencyManagement()
 * 确保 BOM 和强制版本约束正确应用。
 */

plugins {
    id("patra.java-base")
    id("org.springframework.boot")
    // 注意：org.springframework.boot 会自动应用 io.spring.dependency-management
}

// 预编译脚本插件需要显式获取 Version Catalog
val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// 重新应用依赖管理配置
// 虽然 patra.java-base 已经配置过，但 org.springframework.boot 插件会重置配置
applyPatraDependencyManagement(libs)

// ==================== Spring Boot JAR 配置 ====================
tasks.bootJar {
    archiveClassifier = ""
}

// 禁用普通 JAR（只生成 fat JAR）
tasks.jar {
    enabled = false
}

// ==================== bootRun 配置 ====================
tasks.bootRun {
    // OTel Agent JVM 参数
    jvmArgs(
        "-javaagent:${rootProject.projectDir}/../patra-infra/docker/opentelemetry-javaagent.jar",
        "-Dotel.service.name=${project.name}",
        "-Dotel.exporter.otlp.endpoint=http://localhost:4317",
        "-Dotel.exporter.otlp.protocol=grpc",
        "-Dotel.traces.exporter=otlp"
    )
}

dependencies {
    // 服务发现
    implementation(libs.findLibrary("spring-cloud-starter-consul-discovery").get())

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
    testImplementation(libs.findLibrary("testcontainers-jdbc").get())
}
