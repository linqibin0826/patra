/**
 * Patra Catalog Infrastructure
 *
 * 基础设施层 - PostgreSQL 持久化、外部服务集成
 */

plugins {
    id("patra.hexagonal-infra")
}

dependencies {
    // 内部模块
    api(project(":patra-api:patra-catalog:patra-catalog-domain"))
    api(project(":patra-tool:patra-tool-jpa"))
    api(project(":patra-tool:patra-tool-batch"))
    api(project(":patra-tool:patra-tool-http-client"))
    api(project(":patra-api:patra-registry:patra-registry-api"))

    // 对象存储：VenueCoverImageDownloadAdapter 依赖此 starter 上传封面图到 MinIO/S3
    api(project(":patra-tool:patra-tool-object-storage"))

    // WebFlux（WebClient 流式下载）
    api("org.springframework:spring-webflux")
    api("io.projectreactor.netty:reactor-netty-http")

    // Apache Commons Net（FTP 客户端）
    api(libs.commons.net)

    // MapStruct 由 patra.hexagonal-infra 插件提供
    // annotationProcessor 由 patra.java-base 插件提供

    // ICU4J（Unicode 国际化支持）
    api(libs.icu4j)

    // Jsoup（HTML 解析，LetPub 爬虫）
    implementation(libs.jsoup)
    // 复制引用与 CSL-JSON 导出：citeproc-java + 官方 CSL 样式与 locale
    implementation(libs.citeproc.java)
    implementation(libs.citation.styles)
    implementation(libs.citation.locales)
    // WebP 图片尺寸读取（ImageIO SPI 插件，代码只用 javax.imageio）
    runtimeOnly(libs.twelvemonkeys.imageio.webp)

    // Spring Boot Starter
    api("org.springframework.boot:spring-boot-starter")

    // 测试依赖（基础由 patra.java-library 提供）
    testImplementation(project(":patra-tool:patra-tool-test"))
    testImplementation(libs.mockftpserver)
    // Apache HttpClient 5：隧道代理连通性测试需要（starter-rest-client 中为 compileOnly）
    // 对象存储配置带 @Validated；服务里由 patra-tool-web 带进校验器，infra 的 IT 自己补（同 identity-infra）。
    // 有了校验实现，Hibernate 也和生产一样不再自做非空检查
    integrationTestImplementation("org.springframework.boot:spring-boot-starter-validation")
}

// 生成 test-jar 供 boot 模块复用
val testJar by tasks.registering(Jar::class) {
    archiveClassifier = "tests"
    from(sourceSets.test.get().output)
}

configurations {
    create("testArtifacts") {
        extendsFrom(configurations.testImplementation.get())
    }
}

artifacts {
    add("testArtifacts", testJar)
}
