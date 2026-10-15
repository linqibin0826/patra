/**
 * Patra Spring Boot Starter - JPA
 *
 * Spring Data JPA 基础设施：
 * - BaseJpaEntity with @SoftDelete
 * - JPA Auditing integration
 * - Error mapping via ErrorMappingContributor SPI
 * - Batch processing support
 * - Flyway database migration
 */

plugins {
    id("patra.tool-module")
    id("patra.spring-starter")
}

dependencies {
    // Patra 内部依赖
    api(project(":patra-tool:patra-tool-core"))
    compileOnly(project(":patra-tool:patra-tool-web"))
    // 契约测试要能看到 ErrorMappingContributor
    testImplementation(project(":patra-tool:patra-tool-web"))

    // Spring Data JPA (包含 Hibernate)
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    // Spring Boot 自动配置支持
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Jackson for JSON type handling
    api("tools.jackson.core:jackson-databind")

    // Flyway 数据库迁移
    api("org.springframework.boot:spring-boot-starter-flyway")
    api("org.flywaydb:flyway-database-postgresql")

    // PostgreSQL 驱动
    api("org.postgresql:postgresql")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // IDE 配置元数据生成
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
}
