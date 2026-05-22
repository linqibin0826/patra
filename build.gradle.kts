/**
 * Patra - Root Build Script
 *
 * 根项目负责：
 * - 聚合项目信息（version）
 * - 跨模块 JaCoCo 报告聚合（jacoco-report-aggregation plugin）
 *
 * 实际构建逻辑在 build-logic Convention Plugins 中。
 *
 * 常用命令：
 * - ./gradlew clean                                清理所有模块
 * - ./gradlew build                                构建所有模块
 * - ./gradlew test                                 跑所有 unit 测试
 * - ./gradlew integrationTest                      跑所有 IT（需 Docker）
 * - ./gradlew e2eTest                              跑所有 E2E（需 Docker）
 * - ./gradlew testCodeCoverageReport               产出 unit 覆盖率聚合 XML
 * - ./gradlew integrationTestCodeCoverageReport    产出 IT 覆盖率聚合 XML
 * - ./gradlew spotlessApply                        格式化所有代码（PAP-13 启用基线后）
 */

plugins {
    base
    `jacoco-report-aggregation`
    // 与 build-logic/linqibin.java-base 中应用的同名插件版本一致（来自 gradle/libs.versions.toml 的 spring-dependency-management）
    id("io.spring.dependency-management") version "1.1.7"
}

// ==================== Project Info ====================
// group 由 build-logic 中的身份 convention plugin 显式设置（linqibin.module-commons / linqibin.module-patra）。
allprojects {
    version = property("patraVersion") as String
}

// ==================== Dependency Management ====================
// jacoco-report-aggregation 在 resolve 子项目依赖时需要相同的 BOM imports，
// 与 build-logic/LinqibinDependencyManagement.kt 中的 BOM 列表保持一致。
val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.findVersion("spring-boot").get().requiredVersion}")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.findVersion("spring-cloud").get().requiredVersion}")
        mavenBom("com.alibaba.cloud:spring-cloud-alibaba-dependencies:${libs.findVersion("spring-cloud-alibaba").get().requiredVersion}")
        mavenBom("io.github.resilience4j:resilience4j-bom:${libs.findVersion("resilience4j").get().requiredVersion}")
        mavenBom("org.testcontainers:testcontainers-bom:${libs.findVersion("testcontainers").get().requiredVersion}")
    }
}

// ==================== JaCoCo Aggregation ====================
// 把所有 apply 了 jacoco plugin 的子项目纳入聚合
dependencies {
    subprojects {
        plugins.withId("jacoco") {
            jacocoAggregation(this@subprojects)
        }
    }
}

reporting {
    reports {
        register<JacocoCoverageReport>("testCodeCoverageReport") {
            testSuiteName = "test"
        }
        register<JacocoCoverageReport>("integrationTestCodeCoverageReport") {
            testSuiteName = "integrationTest"
        }
        // e2eTest 暂不聚合：3 个 E2E 测试覆盖率信号弱，需要时按相同模式追加
    }
}
