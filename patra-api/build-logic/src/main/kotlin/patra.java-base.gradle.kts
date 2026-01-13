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
}

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
        googleJavaFormat("1.29.0")
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
    toolVersion = "0.8.14"
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
dependencies {
    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.40")
    annotationProcessor("org.projectlombok:lombok:1.18.40")
    testCompileOnly("org.projectlombok:lombok:1.18.40")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.40")

    // MapStruct
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    testAnnotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    // Lombok-MapStruct Binding
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    testAnnotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
}
