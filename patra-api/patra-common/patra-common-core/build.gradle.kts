/**
 * Patra Common Core
 *
 * 所有 Patra 服务的核心工具库：
 * - 领域层基类 (AggregateRoot, DomainEvent)
 * - 错误处理框架 (DomainException, ErrorTrait)
 * - 共享枚举 (ProvenanceCode, Priority)
 * - JSON 工具 (JsonMapperHolder, JsonNormalizer)
 */

plugins {
    id("patra.java-library")
}

dependencies {
    // Hutool 工具库
    api(libs.hutool.core)

    // Jackson JSON 处理 (Spring Boot 4.0 使用 tools.jackson)
    api("tools.jackson.core:jackson-core")
    api("tools.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.core:jackson-annotations")

    // SLF4J API (运行时由容器提供)
    compileOnly("org.slf4j:slf4j-api")

    // 测试依赖由 patra.java-library 插件提供
}

// 覆盖率要求 75%
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}
