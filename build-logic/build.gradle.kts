/**
 * Build Logic Build Script
 *
 * 定义 Convention Plugins 的依赖
 * 这里引入的插件可以在 Convention Plugins 中使用
 */

plugins {
    `kotlin-dsl`
}

dependencies {
    // Spring Boot Plugin - 用于 boot 模块的 fat JAR 打包
    implementation("org.springframework.boot:spring-boot-gradle-plugin:${libs.versions.spring.boot.get()}")

    // Spring Dependency Management - BOM 导入
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:${libs.versions.spring.dependency.management.get()}")

    // Spotless - 代码格式化 (替代 google-fmt)
    implementation("com.diffplug.spotless:spotless-plugin-gradle:${libs.versions.spotless.get()}")

    // SpotBugs - 静态代码分析
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:${libs.versions.spotbugs.plugin.get()}")

    // Kordamp Enforcer - 依赖约束 (替代 maven-enforcer-plugin)
    implementation("org.kordamp.gradle:enforcer-gradle-plugin:${libs.versions.kordamp.enforcer.get()}")
}
