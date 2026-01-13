/**
 * Patra Spring Library Convention Plugin
 *
 * 用于需要 Spring 依赖管理但不是 Boot 应用的模块
 * 如: Starter 模块、infra 层、adapter 层等
 */

plugins {
    id("patra.java-library")
    id("io.spring.dependency-management")
}

// 导入 Spring Boot BOM 进行依赖版本管理
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.1")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.0")
        mavenBom("io.github.resilience4j:resilience4j-bom:2.2.0")
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
        mavenBom("software.amazon.awssdk:bom:2.25.36")
    }
}

// 强制版本约束，解决依赖冲突
configurations.all {
    resolutionStrategy {
        // Testcontainers vs MinIO 版本冲突
        force("org.apache.commons:commons-compress:1.28.0")
        // Elasticsearch 依赖冲突
        force("org.apache.httpcomponents:httpclient:4.5.14")
        // Mockito vs Kryo 版本冲突
        force("org.objenesis:objenesis:3.4")
    }
}
