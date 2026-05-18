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
 * 但 patra.java-base 中的 BOM 配置不会自动继承。这里通过 applyLinqibinDependencyManagement()
 * 确保 BOM 和强制版本约束正确应用。
 */

plugins {
    id("linqibin.java-base")
    id("org.springframework.boot")
    // 注意：org.springframework.boot 会自动应用 io.spring.dependency-management
}

// 预编译脚本插件需要显式获取 Version Catalog
val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// 重新应用依赖管理配置
// 虽然 patra.java-base 已经配置过，但 org.springframework.boot 插件会重置配置
applyLinqibinDependencyManagement(libs)

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
    // bootRun 退出 Configuration Cache:
    // (1) OTel agent 配置在 configuration 阶段求值,每次变化都让 cc 失效(见下方 NOTE);
    // (2) bootRun 与 publishToMavenLocal 等任务复用同一 cc entry 时会污染 runtime classpath
    //     (曾出现 catalog-infra 编译期看不到 patra-common-model 的诡异错误)。
    // 主动 mark 让 Gradle 跳过 cc 复用,每次重新算 task graph,避免污染。
    notCompatibleWithConfigurationCache(
        "bootRun reads OTel config at configuration time and depends on full runtime classpath; " +
            "Configuration Cache reuse across builds causes classpath corruption in Gradle 9 + Spring Boot 4."
    )

    // 从 gradle.properties 读取 OTel 配置
    // NOTE: 属性值在配置阶段求值，变化时会触发 Configuration Cache 失效（这是预期行为）
    // 对于 bootRun 开发任务，这完全可接受，因为 OTel 配置很少变化
    val otelAgentPath = providers.gradleProperty("otel.agent.path").getOrElse("")
    val otelExporterEndpoint = providers.gradleProperty("otel.exporter.endpoint").getOrElse("http://localhost:4317")

    // OTel Agent JVM 参数（仅当配置了 agent 路径时启用）
    if (otelAgentPath.isNotBlank()) {
        val agentJar = rootProject.projectDir.resolve(otelAgentPath)
        jvmArgs(
            "-javaagent:$agentJar",
            "-Dotel.service.name=${project.name}",
            "-Dotel.exporter.otlp.endpoint=$otelExporterEndpoint",
            "-Dotel.exporter.otlp.protocol=grpc",
            "-Dotel.traces.exporter=otlp"
        )
    }
}

dependencies {
    // 服务发现
    implementation(libs.findLibrary("spring-cloud-starter-alibaba-nacos-discovery").get())

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
    testImplementation(libs.findLibrary("testcontainers-jdbc").get())
}
