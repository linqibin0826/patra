/**
 * Patra Hexagonal Application Layer Convention Plugin
 *
 * 应用层 - 用例编排
 *
 * 职责：
 * - Use Case 实现
 * - 事务边界 (@Transactional)
 * - 领域服务编排
 * - DTO <-> Domain 转换
 */

plugins {
    id("patra.spring-library")
}

// 预编译脚本插件需要显式获取 Version Catalog
val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// 使用 Version Catalog (libs) 声明依赖
dependencies {
    // MapStruct (用于 DTO 映射)
    implementation(libs.findLibrary("mapstruct").get())

    // Hibernate Validator (校验)
    implementation("org.hibernate.validator:hibernate-validator")

    // Spring Transaction (用于 @Transactional)
    implementation("org.springframework:spring-tx")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
