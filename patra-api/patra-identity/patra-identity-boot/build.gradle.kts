/**
 * Patra Identity Boot
 *
 * 启动层 - Spring Boot 应用入口
 */

plugins {
    id("patra.hexagonal-boot")
}

springBoot {
    mainClass = "dev.patra.identity.PatraIdentityApplication"
}

// 头像的 WebP 编码器（webp-imageio）用 System.load 加载 jar 里的 libwebp：JDK 25 对未命名模块只告警，
// 以后的版本默认拦截。可执行 jar 的清单属性对 `java -jar` 生效
tasks.bootJar {
    manifest { attributes("Enable-Native-Access" to "ALL-UNNAMED") }
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-api:patra-identity:patra-identity-adapter"))
    implementation(project(":patra-api:patra-identity:patra-identity-infra"))

    implementation(project(":patra-tool:patra-tool-web"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":patra-tool:patra-tool-observability"))
    implementation(project(":patra-tool:patra-tool-openapi"))

    // 从网关签的断言取当前用户：过滤器链、CurrentUserPort、401 / 403 输出、JPA 审计人
    implementation(project(":patra-tool:patra-tool-security"))

    // 装配 RedisSessionStore 要用 StringRedisTemplate
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // 装配 catalog 内部接口的 HTTP Interface 代理（PAP-95）
    implementation(project(":patra-api:patra-catalog:patra-catalog-api"))
    implementation(project(":patra-tool:patra-tool-http-client"))

    // 头像存专用桶
    implementation(project(":patra-tool:patra-tool-object-storage"))

    // 安全邮件改发事件（PAP-104）：事件发布登记 + 外送 RabbitMQ；映射成 -api 里的集成事件
    implementation(project(":patra-tool:patra-tool-events"))
    implementation(project(":patra-api:patra-identity:patra-identity-api"))

    testImplementation(project(":patra-tool:patra-tool-test"))
    testImplementation(testFixtures(project(":patra-api:patra-identity:patra-identity-infra")))

    // 测试里自动注入测试公钥，并能签出带身份的请求头
    testImplementation(testFixtures(project(":patra-tool:patra-tool-security")))

    // 安全邮件经登记表序列化器往返的 IT 要直接用 Modulith 的 EventSerializer（事件模块只在 implementation 里带它）
    integrationTestImplementation("org.springframework.modulith:spring-modulith-events-core")
}

// 装配了 ip2region 适配器的测试上下文要有数据文件（PAP-99）：用 infra 按 ip2region.properties 下好的那份，
// 经 infra 对外提供的 ip2regionData 取得（下载任务自动排在测试之前，目录内容是测试的输入）
val ip2regionDataScope = configurations.dependencyScope("ip2regionData")
val ip2regionData = configurations.resolvable("ip2regionDataFiles") { extendsFrom(ip2regionDataScope.get()) }
dependencies {
    "ip2regionData"(project(path = ":patra-api:patra-identity:patra-identity-infra", configuration = "ip2regionData"))
}
tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(DirectorySystemProperty("patra.identity.ip-geolocation.directory", ip2regionData.get()))
}
