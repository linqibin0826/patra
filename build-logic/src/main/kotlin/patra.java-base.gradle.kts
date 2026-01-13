/**
 * Patra Java Base Convention Plugin
 *
 * 所有 Java 模块的基础配置：
 * - Java 25 Toolchain
 * - 编译选项
 * - Spotless 代码格式化
 * - SpotBugs 静态分析
 * - JaCoCo 代码覆盖率
 * - Lombok + MapStruct 注解处理器
 */

plugins {
    java
    jacoco
    id("com.diffplug.spotless")
    id("com.github.spotbugs")
    id("io.spring.dependency-management")
}

// ==================== Version Catalog 访问 ====================
// 预编译脚本插件需要显式获取 Version Catalog
val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// ==================== 统一依赖管理（BOM + 强制版本约束）====================
// 配置定义在 PatraDependencyManagement.kt 中，版本从 libs.versions.toml 获取
applyPatraDependencyManagement(libs)

// ==================== Java Toolchain ====================
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// ==================== Compile Options ====================
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // 保留方法参数名，用于 Spring MVC 参数绑定
    options.compilerArgs.addAll(listOf("-parameters"))
}

// ==================== Spotless (Code Formatting) ====================
spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat(libs.findVersion("google-java-format").get().requiredVersion)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ==================== SpotBugs (Static Analysis) ====================
spotbugs {
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    // 排除配置文件路径
    excludeFilter = rootProject.file("spotbugs-exclude.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
    reports.create("xml") { required = true }
}

// ==================== JaCoCo (Code Coverage) ====================
jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    // 排除第三方库中过大的类
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude("net/sf/jsqlparser/**")
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                // 默认 70% 覆盖率
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

// ==================== Test Configuration ====================
tasks.test {
    useJUnitPlatform {
        // 排除手动测试
        excludeTags("manual")
    }

    // 并行执行
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    // Mockito Agent 配置
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED"
    )

    testLogging {
        events("passed", "skipped", "failed")
    }
}

// ==================== Annotation Processors ====================
// 使用 Version Catalog (libs) 声明依赖
dependencies {
    // Lombok
    compileOnly(libs.findLibrary("lombok").get())
    annotationProcessor(libs.findLibrary("lombok").get())
    testCompileOnly(libs.findLibrary("lombok").get())
    testAnnotationProcessor(libs.findLibrary("lombok").get())

    // MapStruct
    annotationProcessor(libs.findLibrary("mapstruct-processor").get())
    testAnnotationProcessor(libs.findLibrary("mapstruct-processor").get())

    // Lombok-MapStruct Binding
    annotationProcessor(libs.findLibrary("lombok-mapstruct-binding").get())
    testAnnotationProcessor(libs.findLibrary("lombok-mapstruct-binding").get())

    // JUnit Platform Launcher - Gradle 9 不再自动添加，需要显式声明
    // See: https://github.com/gradle/gradle/issues/34512
    // See: https://github.com/spring-projects/spring-boot/issues/46037
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
