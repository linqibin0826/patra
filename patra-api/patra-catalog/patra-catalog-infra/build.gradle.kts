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
    api("commons-net:commons-net:3.11.1")

    // RocketMQ
    api("org.apache.rocketmq:rocketmq-spring-boot-starter:2.3.1")

    // MapStruct
    api("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // ICU4J（Unicode 国际化支持）
    api("com.ibm.icu:icu4j:76.1")

    // Spring Boot Starter
    api("org.springframework.boot:spring-boot-starter")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
    testImplementation("org.mockftpserver:MockFtpServer:3.2.0")
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
