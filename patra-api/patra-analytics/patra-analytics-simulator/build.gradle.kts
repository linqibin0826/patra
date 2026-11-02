import java.util.Properties

/**
 * Patra Analytics Simulator - 流量模拟器：按真实访问规律造合成事件，灌历史直写 ClickHouse、实时压测打采集端点，记真值并对账
 *
 * 独立命令行，不依赖服务内部代码；api 模块的 schema 与爬虫列表、infra 的富化适配器只在测试里用来核对一致性
 */

plugins {
    id("patra.java-base")
    application
}

application {
    mainClass = "dev.patra.analytics.simulator.SimulatorMain"
}

dependencies {
    // 命令行解析
    implementation(libs.picocli)
    // 灌历史、对账、报告读写 ClickHouse（与入库同一个官方客户端）
    implementation(libs.clickhouse.client)
    // 事件、真值摘要、模型参数的 JSON
    implementation("tools.jackson.core:jackson-databind")

    testImplementation(libs.bundles.testing)
    // 生成的事件按服务共用的 JSON Schema 校验；爬虫列表
    testImplementation(project(":patra-api:patra-analytics:patra-analytics-api"))
    testImplementation(libs.json.schema.validator)
    // UA 表、IP 表与服务的富化结果对齐；集成测试复用建表迁移器
    testImplementation(project(":patra-api:patra-analytics:patra-analytics-infra"))
    // 测试用的小文献池（src/testFixtures/resources）；单元测试由 java-test-fixtures 自动带上
    integrationTestImplementation(testFixtures(project()))
    integrationTestImplementation(project(":patra-tool:patra-tool-test"))
    integrationTestImplementation(libs.testcontainers.clickhouse)
}

// 核对 IP 表要用 ip2region 数据文件：照 analytics-infra，下载只归 identity-infra 一处，这里只排在它之后读同一个目录
val ip2regionTag =
    Properties()
        .apply { rootProject.file("patra-infra/scripts/ip2region.properties").inputStream().use { load(it) } }
        .getProperty("tag")
val ip2regionFiles = files(File(gradle.gradleUserHomeDir, "caches/patra/ip2region/$ip2regionTag"))
tasks.withType<Test>().configureEach {
    dependsOn(":patra-api:patra-identity:patra-identity-infra:downloadIp2RegionData")
    jvmArgumentProviders.add(DirectorySystemProperty("patra.analytics.ip-geolocation.directory", ip2regionFiles))
}
