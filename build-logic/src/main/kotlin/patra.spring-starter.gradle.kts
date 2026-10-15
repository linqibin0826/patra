/**
 * Patra Spring Boot Starter Convention Plugin
 *
 * 用于自定义 Starter 模块的配置
 * Starter 提供自动配置，但不是可执行应用
 */

plugins {
    id("patra.spring-library")
}

dependencies {
    // 配置元数据与自动配置元数据（Boot 官方要求 autoconfigure 模块都加）
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    // record 配置类上的 @Validated / @Min 等；运行时没有校验器就当没写
    compileOnly("jakarta.validation:jakarta.validation-api")
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
