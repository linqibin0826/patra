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
    // 使用 plugin marker artifact，与 spotbugs 依赖声明形式对称。
    implementation("com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:${libs.versions.spotless.get()}")

    // SpotBugs - 静态代码分析
    implementation("com.github.spotbugs:com.github.spotbugs.gradle.plugin:${libs.versions.spotbugs.plugin.get()}")

    // 安全版本约束：spring-boot-gradle-plugin 经 commons-compress 1.27.1 传递引入有漏洞的 commons-lang3 3.16.0
    constraints {
        implementation("org.apache.commons:commons-lang3:${libs.versions.commons.lang3.get()}")
    }
}
