/**
 * Patra Hexagonal Domain Layer Convention Plugin
 *
 * 领域层 - 六边形架构的核心
 *
 * 关键约束：领域层必须是框架无关的纯 Java 代码
 * - 禁止 Spring Framework
 * - 禁止 Spring Boot
 * - 禁止 Jakarta EE (JPA, Validation, Servlet)
 * - 禁止持久化框架 (Hibernate)
 * - 禁止 Web 容器 (Tomcat, Netty)
 *
 * 允许：
 * - patra-common-core
 * - Lombok
 * - Hutool
 * - Jackson (序列化)
 * - 测试框架
 */

plugins {
    id("patra.java-library")
    id("org.kordamp.gradle.project-enforcer")
}

// ==================== 领域层纯净性强制规则 ====================
enforce {
    rule(enforcer.rules.BannedDependencies::class.java) {
        // 检查编译时和运行时依赖
        configurations.addAll(listOf("compileClasspath", "runtimeClasspath"))

        // 禁止 Spring Framework
        exclude("org.springframework:*")
        exclude("org.springframework.boot:*")
        exclude("org.springframework.cloud:*")
        exclude("org.springframework.data:*")
        exclude("org.springframework.security:*")

        // 禁止 Jakarta EE
        exclude("jakarta.persistence:*")
        exclude("jakarta.validation:*")
        exclude("jakarta.annotation:*")
        exclude("jakarta.servlet:*")
        exclude("jakarta.transaction:*")

        // 禁止持久化框架
        exclude("org.hibernate:*")
        exclude("org.hibernate.orm:*")

        // 禁止 Web 容器
        exclude("org.apache.tomcat:*")
        exclude("org.apache.tomcat.embed:*")
        exclude("io.netty:*")

        message.set("""
            |
            |❌ DOMAIN LAYER VIOLATION - Hexagonal Architecture Constraint
            |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            |
            |The domain layer MUST be framework-free!
            |
            |✅ Allowed dependencies:
            |   - patra-common-core
            |   - Lombok, Hutool, Jackson
            |   - Test frameworks (JUnit, AssertJ, Mockito)
            |
            |❌ Forbidden dependencies:
            |   - Spring Framework / Boot / Cloud / Data
            |   - Jakarta EE (JPA, Validation, Servlet)
            |   - Hibernate, Tomcat, Netty
            |
            |Domain layer should contain pure business logic only.
            |Use Ports (interfaces) to abstract infrastructure concerns.
            |
            |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        """.trimMargin())
    }
}

dependencies {
    // 领域层核心依赖
    api(project(":patra-common:patra-common-core"))

    // Hutool 工具库
    implementation("cn.hutool:hutool-core:5.8.25")

    // MapStruct (用于值对象映射)
    implementation("org.mapstruct:mapstruct:1.6.3")

    // 测试依赖
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.15.2")
}
