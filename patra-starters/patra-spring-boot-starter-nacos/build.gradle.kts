/**
 * Patra Spring Boot Starter - Nacos
 *
 * Patra 服务接入 Nacos：
 * - 统一注入 Nacos 连接和服务发现设置（所有启动方式都生效，包括测试）
 * - 启动器 PatraApplication 加入 patra-common.yaml、{服务名}.yaml 两条导入
 * - 签到检查：拿不到 Nacos 配置时启动失败
 * - 本机读取 ~/.patra/secrets 下的 .env 文件
 *
 * 设计：docs/patra/specs/2026-10-13-nacos-config-starter-design.md
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.spring-boot-starter")
}

dependencies {
    // EnvironmentPostProcessor、SpringApplication 等：SCA 的 starter 不把 Spring Boot 传给编译 classpath
    api("org.springframework.boot:spring-boot-starter")

    // 配置中心：只用 spring.config.import 的 nacos: 导入，不引入 bootstrap（它会让动态刷新失效）
    api(libs.spring.cloud.starter.alibaba.nacos.config)

    // 服务发现
    api(libs.spring.cloud.starter.alibaba.nacos.discovery)

    // 集成测试：Testcontainers 起 Nacos；Awaitility 等配置刷新
    "integrationTestImplementation"(libs.bundles.testcontainers)
    "integrationTestImplementation"(libs.awaitility)
    // Spring Cloud 的服务注册引用 WebServerInitializedEvent；Patra 的服务都带 web，测试应用没有
    "integrationTestImplementation"("org.springframework.boot:spring-boot-web-server")
}

// SCA 的 NacosConfigManager 在进程里是静态单例：连哪个地址、用哪个账号，由进程里第一个启动的
// 应用决定；nacos-client 的快照目录也在类加载时固定。地址或账号不同的集成测试类各用一个 JVM。
tasks.named<Test>("integrationTest") {
    forkEvery = 1
}
