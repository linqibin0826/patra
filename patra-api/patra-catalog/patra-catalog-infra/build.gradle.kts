/**
 * Patra Catalog Infrastructure
 *
 * 基础设施层 - MySQL/Elasticsearch 持久化、外部服务集成
 */

plugins {
    id("patra.hexagonal-infra")
}

dependencies {
    // 内部模块
    api(project(":patra-catalog:patra-catalog-domain"))
    api(project(":patra-common:patra-common-model"))
    api(project(":patra-spring-boot-starter-jpa"))
    api(project(":patra-spring-boot-starter-batch"))
    api(project(":patra-spring-boot-starter-rest-client"))
    api(project(":patra-spring-boot-starter-http-interface"))
    api(project(":patra-registry:patra-registry-api"))
    api(project(":patra-spring-boot-starter-core"))

    // 对象存储（可选）
    compileOnly(project(":patra-spring-boot-starter-object-storage"))

    // Elasticsearch
    api("org.springframework.boot:spring-boot-starter-data-elasticsearch")

    // WebFlux（WebClient 流式下载）
    api("org.springframework:spring-webflux")
    api("io.projectreactor.netty:reactor-netty-http")

    // Apache Commons Net（FTP 客户端）
    api(libs.commons.net)

    // RocketMQ
    api(libs.rocketmq.spring.boot)

    // MapStruct 由 patra.hexagonal-infra 插件提供
    // annotationProcessor 由 patra.java-base 插件提供

    // ICU4J（Unicode 国际化支持）
    api(libs.icu4j)

    // Jsoup（HTML 解析，LetPub 爬虫）
    implementation(libs.jsoup)

    // Spring Boot Starter
    api("org.springframework.boot:spring-boot-starter")

    // 测试依赖（基础由 patra.java-library 提供）
    testImplementation(project(":patra-spring-boot-starter-test"))
    testImplementation(libs.mockftpserver)
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
