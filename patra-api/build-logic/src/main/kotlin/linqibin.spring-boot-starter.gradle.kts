/**
 * Patra Spring Boot Starter Convention Plugin
 *
 * 用于自定义 Starter 模块的配置
 * Starter 提供自动配置，但不是可执行应用
 */

plugins {
    id("linqibin.spring-library")
}

dependencies {
    // Configuration Processor 生成 IDE 元数据
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // 测试依赖
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Starter 模块的覆盖率要求更高
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}
