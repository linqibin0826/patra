import java.util.Properties

/**
 * Patra Analytics Infrastructure - 端口实现：JSON Schema 校验、UA 解析、爬虫列表、频率规则、地区、Kafka 写入
 */

plugins {
    id("patra.hexagonal-infra")
}

dependencies {
    // 实现领域层的端口
    api(project(":patra-api:patra-analytics:patra-analytics-domain"))
    // PostgreSQL 数据源与 Flyway（阅读历史 PAP-169 起才建表）
    api(project(":patra-tool:patra-tool-jpa"))
    // 事件格式校验：networknt 3.x 是唯一用 Jackson 3 的版本线
    implementation(libs.json.schema.validator)
    // 入库：ClickHouse 官方客户端（HTTP 协议）
    implementation(libs.clickhouse.client)
    // 注销清理查 identity 的账号状态：内部接口的契约与 HTTP Interface 的错误类型；代理在 boot 里装配
    implementation(project(":patra-api:patra-identity:patra-identity-api"))
    implementation(project(":patra-tool:patra-tool-http-client"))
    // JSON Schema、样例、COUNTER-Robots 列表都是 api 模块的资源，只在运行时按 classpath 读，不 import 它的类
    runtimeOnly(project(":patra-api:patra-analytics:patra-analytics-api"))
    testImplementation(project(":patra-api:patra-analytics:patra-analytics-api"))
    // UA 解析与爬虫设备类别
    implementation(libs.yauaa)
    // IP 查国家与省份（数据文件在 ~/.patra/data/ip2region，与 identity 共用）
    implementation(libs.ip2region)
    // 爬虫频率规则的滑动窗口
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // 写 events.raw / events.dlq
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    // 写 Kafka 的耗时指标
    implementation("io.micrometer:micrometer-core")
    // 集成测试的 Redis / Kafka 容器初始化器
    integrationTestImplementation(project(":patra-tool:patra-tool-test"))
    // boot 集成测试的 PG 容器初始化器
    testFixturesImplementation(project(":patra-tool:patra-tool-test"))
}

// 子域包只有 package-info.java 时也产出 package-info.class：架构测试据此确认每个子域都被导入，
// 层规则也不会因为层为空而失败。javac 默认只给带注解的 package-info 产出 class
tasks.compileJava {
    options.compilerArgs.add("-Xpkginfo:always")
}

// 测试用的 ip2region 数据文件：下载只归 identity-infra 一处（两边各注册下载任务会写同一个缓存目录，Gradle 判成隐式依赖、
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
