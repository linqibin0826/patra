/**
 * linqibin Commons Core
 *
 * 通用核心工具库（从 patra-common-core 迁出的领域无关类）：
 * - CQRS 基类
 * - DDD 基类 (AggregateRoot, DomainEvent)
 * - 错误处理框架 (DomainException, ErrorTrait)
 * - JSON 工具 (JsonMapperHolder, JsonNormalizer)
 * - 分页、消息、类型、工具
 */

plugins {
    id("linqibin.java-library") // Phase 1.3 后改为 linqibin.java-library
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
