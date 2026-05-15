/**
 * Patra Spring Boot Starter - Provenance
 *
 * Provenance API 客户端：
 * - PubMed API
 * - EPMC API
 * - Crossref API
 */

plugins {
    id("linqibin.spring-boot-starter")
}

dependencies {
    // Patra 内部依赖
    api(project(":patra-common:patra-common-core"))
    api(project(":linqibin-commons-core"))
    api(project(":linqibin-spring-boot-starter-rest-client"))
    api(project(":patra-common:patra-common-model"))
    api(project(":patra-common:patra-common-provenance-api"))

    // Spring Boot AutoConfiguration
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Configuration metadata
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // JSON/XML Processing
    api("tools.jackson.core:jackson-databind")
    api("tools.jackson.dataformat:jackson-dataformat-xml")

    // Utility Libraries
    api(libs.hutool.core)

    // Logging
    api("org.slf4j:slf4j-api")

    // Monitoring (optional)
    compileOnly("io.micrometer:micrometer-core")

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
}
