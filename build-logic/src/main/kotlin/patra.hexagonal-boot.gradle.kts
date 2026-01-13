/**
 * Patra Hexagonal Boot Layer Convention Plugin
 *
 * 启动层 - Spring Boot 应用入口
 *
 * 职责：
 * - @SpringBootApplication 入口
 * - 配置装配
 * - 生成可执行 fat JAR
 */

plugins {
    id("patra.java-base")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// 导入 BOM
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.1")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.0")
        mavenBom("io.github.resilience4j:resilience4j-bom:2.2.0")
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
        mavenBom("software.amazon.awssdk:bom:2.25.36")
    }
}

// 强制版本约束
configurations.all {
    resolutionStrategy {
        force("org.apache.commons:commons-compress:1.28.0")
        force("org.apache.httpcomponents:httpclient:4.5.14")
        force("org.objenesis:objenesis:3.4")
    }
}

// ==================== Spring Boot JAR 配置 ====================
tasks.bootJar {
    archiveClassifier = ""
    // 生成启动脚本（可选）
    launchScript()
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
    implementation("org.springframework.cloud:spring-cloud-starter-consul-discovery")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
    testImplementation("org.testcontainers:jdbc")
}
