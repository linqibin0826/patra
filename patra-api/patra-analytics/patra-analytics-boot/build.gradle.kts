import java.util.Properties

/**
 * Patra Analytics Boot - 启动入口与组装
 */

plugins {
    id("patra.hexagonal-boot")
}

springBoot {
    mainClass = "dev.patra.analytics.PatraAnalyticsApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-api:patra-analytics:patra-analytics-adapter"))
    implementation(project(":patra-api:patra-analytics:patra-analytics-infra"))
    implementation(project(":patra-tool:patra-tool-web"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":patra-tool:patra-tool-observability"))
    implementation(project(":patra-tool:patra-tool-openapi"))
    // XXL-Job 执行器（执行器端口 = 服务端口 + 1 = 6701）；本 Issue 还没有任务
    implementation(libs.xxl.job)
    // 指标经 OTLP 推给 OTel Collector（.claude/rules/tech/observability.md）
    implementation("io.micrometer:micrometer-registry-otlp")
    // 架构测试要看到全部五层；集成测试从 api 的资源里读样例
    testImplementation(project(":patra-api:patra-analytics:patra-analytics-api"))
    // 集成测试：PG 容器初始化器（infra 的 testFixtures）、Kafka / Redis 容器、读 Kafka 的消费者
    testImplementation(testFixtures(project(":patra-api:patra-analytics:patra-analytics-infra")))
    testImplementation(project(":patra-tool:patra-tool-test"))
    testImplementation("org.springframework.kafka:spring-kafka")
}

// 装配了 ip2region 适配器的测试上下文要有数据文件：下载只归 identity-infra 一处（两边各注册下载任务会写同一个缓存目录，Gradle 判成隐式依赖、
// 拒绝执行 identity 的测试）。这里只声明测试排在它的下载任务之后，读同一个目录；不建项目依赖，免得 CD 把 identity 的改动
// 算成 analytics 的影响范围
val ip2regionTag =
    Properties()
        .apply { rootProject.file("patra-infra/scripts/ip2region.properties").inputStream().use { load(it) } }
        .getProperty("tag")
val ip2regionFiles = files(File(gradle.gradleUserHomeDir, "caches/patra/ip2region/$ip2regionTag"))
tasks.withType<Test>().configureEach {
    dependsOn(":patra-api:patra-identity:patra-identity-infra:downloadIp2RegionData")
    jvmArgumentProviders.add(DirectorySystemProperty("patra.analytics.ip-geolocation.directory", ip2regionFiles))
}
