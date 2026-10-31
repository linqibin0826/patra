import java.util.Properties

/**
 * Patra Identity Infrastructure
 *
 * 基础设施层 - 持久化、密码哈希、常见密码名单、登录失败限制
 */

plugins {
    id("patra.hexagonal-infra")
}

dependencies {
    api(project(":patra-api:patra-identity:patra-identity-domain"))
    api(project(":patra-tool:patra-tool-jpa"))

    // 会话与登录失败限制存在 Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // 保存资料时经 catalog 内部接口校验 ROR ID / MeSH UI 并取名称快照（PAP-95）：契约 + 服务间调用能力
    implementation(project(":patra-api:patra-catalog:patra-catalog-api"))
    implementation(project(":patra-tool:patra-tool-http-client"))

    // Argon2 只需要 crypto 模块：不带 Spring Security 的过滤器链和自动配置
    implementation("org.springframework.security:spring-security-crypto")
    runtimeOnly("org.bouncycastle:bcprov-jdk18on")

    // 登录设备（PAP-99）：Yauaa 把 User-Agent 解析成设备名与端。它用 Log4j2 API 打日志，
    // Boot 的 log4j-to-slf4j 已带 log4j-api，日志进 logback；antlr / snakeyaml 等都 shade 在它自己的包里
    implementation(libs.yauaa)

    // 登录设备（PAP-99）：ip2region 离线库查 IP 属地；数据文件不在 jar 里，见下面的 downloadIp2RegionData
    implementation(libs.ip2region)

    // 头像：解码只用纯 Java（TwelveMonkeys 的 JPEG / WebP），WebP 编码用 libwebp 的 JNI 包装
    implementation(libs.twelvemonkeys.imageio.jpeg)
    implementation(libs.twelvemonkeys.imageio.webp)
    implementation(libs.webp.imageio)
    implementation(project(":patra-tool:patra-tool-object-storage"))
    testFixturesImplementation(libs.twelvemonkeys.imageio.webp)
    testFixturesImplementation(libs.webp.imageio)

    testImplementation(project(":patra-tool:patra-tool-test"))
    // 对象存储配置带 @Validated；服务里由 patra-tool-web 带进校验器，infra 的 IT 自己补
    integrationTestImplementation("org.springframework.boot:spring-boot-starter-validation")
}

// ip2region 的数据文件（PAP-99）：不进 git、不进镜像。版本与校验和在 patra-infra/scripts/ip2region.properties
// （mini 只检出 patra-infra，获取脚本要读得到它）；测试用这里按它下载的同一份数据，
// 运行时由 patra-infra/scripts/fetch-ip2region.sh 下到 ~/.patra/data/ip2region/。
// 下到 Gradle 用户目录的缓存里（按 tag 分目录）：clean、换检出都不用重下；校验和对得上的直接跳过。
val ip2region =
    Properties().apply {
        rootProject.file("patra-infra/scripts/ip2region.properties").inputStream().use { load(it) }
    }
val ip2regionDirectory = File(gradle.gradleUserHomeDir, "caches/patra/ip2region/${ip2region.getProperty("tag")}")
val ip2regionDownloads =
    listOf("v4", "v6").map { version ->
        tasks.register<VerifiedDownload>("downloadIp2Region${version.uppercase()}") {
            url.set(
                "https://raw.githubusercontent.com/lionsoul2014/ip2region/" +
                    "${ip2region.getProperty("tag")}/data/ip2region_$version.xdb",
            )
            sha256.set(ip2region.getProperty("$version.sha256"))
            destination.set(File(ip2regionDirectory, "ip2region_$version.xdb"))
        }
    }
val downloadIp2RegionData by tasks.registering {
    description = "下载测试用的 ip2region 数据文件（版本见 ip2region.properties）"
    dependsOn(ip2regionDownloads)
}
val ip2regionFiles = files(ip2regionDirectory).builtBy(downloadIp2RegionData)

// 数据目录作为 ip2regionData 对外提供：identity-boot 的测试从这里取，不写死路径
configurations.consumable("ip2regionData") {
    outgoing.artifact(ip2regionDirectory) { builtBy(downloadIp2RegionData) }
}

tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(DirectorySystemProperty("patra.identity.ip-geolocation.directory", ip2regionFiles))
}
