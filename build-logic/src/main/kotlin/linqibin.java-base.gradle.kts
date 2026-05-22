/**
 * Patra Java Base Convention Plugin
 *
 * 所有 Java 模块的基础配置：
 * - Java 25 Toolchain
 * - 编译选项
 * - Spotless 代码格式化
 * - SpotBugs 静态分析
 * - JaCoCo 代码覆盖率
 * - JVM Test Suites（test / integrationTest / e2eTest）
 * - Lombok + MapStruct 注解处理器
 */

plugins {
    java
    jacoco
    `java-test-fixtures`
    id("linqibin.spotless")
    id("linqibin.spotbugs")
    id("io.spring.dependency-management")
}

// ==================== Version Catalog 访问 ====================
val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// ==================== 统一依赖管理 ====================
applyLinqibinDependencyManagement(libs)

// ==================== Java Toolchain ====================
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// ==================== Compile Options ====================
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters"))
}

// ==================== Spotless / SpotBugs ====================
// 已抽出为独立 convention plugins：linqibin.spotless / linqibin.spotbugs
// 见 build-logic/src/main/kotlin/linqibin.{spotless,spotbugs}.gradle.kts
// 由本文件 plugins 块通过 id("linqibin.spotless") / id("linqibin.spotbugs") 引入。

// ==================== JaCoCo ====================
jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
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
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}
// 注意：jacocoTestCoverageVerification 不挂到 check（决策 D：v0.3 不设阈值门禁）

// ==================== JVM Test Suites ====================
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
            targets.configureEach {
                testTask.configure {
                    useJUnitPlatform {
                        excludeTags("manual")
                        if (!project.hasProperty("runExternal")) excludeTags("external")
                    }
                    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                    forkEvery = 50
                    systemProperty("junit.jupiter.execution.timeout.testable.method.default", "15s")
                    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
                    testLogging { events("passed", "skipped", "failed") }
                }
            }
        }

        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
            dependencies {
                implementation(project())
            }
            targets.configureEach {
                testTask.configure {
                    shouldRunAfter(test)
                    useJUnitPlatform {
                        excludeTags("manual")
                        if (!project.hasProperty("runExternal")) excludeTags("external")
                    }
                    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                    forkEvery = 50
                    systemProperty("junit.jupiter.execution.timeout.testable.method.default", "30s")
                    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
                    testLogging { events("passed", "skipped", "failed") }
                }
            }
        }

        register<JvmTestSuite>("e2eTest") {
            useJUnitJupiter()
            dependencies {
                implementation(project())
            }
            targets.configureEach {
                testTask.configure {
                    shouldRunAfter(named("integrationTest"))
                    useJUnitPlatform {
                        excludeTags("manual")
                        if (!project.hasProperty("runExternal")) excludeTags("external")
                    }
                    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                    forkEvery = 50
                    systemProperty("junit.jupiter.execution.timeout.testable.method.default", "180s")
                    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
                    testLogging { events("passed", "skipped", "failed") }
                }
            }
        }
    }
}

// ==================== Test Suite 之间的配置/source 共享 ====================
// integrationTest 与 e2eTest 各自的 Implementation/RuntimeOnly/CompileOnly 配置
// 默认是独立的；这里让它们继承 test 的 declarative 依赖，避免每模块重复声明。
listOf("integrationTest", "e2eTest").forEach { suiteName ->
    configurations.named("${suiteName}Implementation") {
        extendsFrom(configurations.named("testImplementation").get())
    }
    configurations.named("${suiteName}RuntimeOnly") {
        extendsFrom(configurations.named("testRuntimeOnly").get())
    }
    configurations.named("${suiteName}CompileOnly") {
        extendsFrom(configurations.named("testCompileOnly").get())
    }
}

// integrationTest 复用 test source set 输出（共享 unit/IT 都用的 TestDataBuilder / Helper 等）
// e2eTest 同时继承 integrationTest 与 test output（链式共享 *ContainerInitializer / test fixtures）
//
// 同时显式补充 main source set output 到 compileClasspath / runtimeClasspath:
// Spring Boot plugin 默认禁用 `jar` task（只产 bootJar），导致 `implementation(project())`
// 引用的 default configuration artifact 为空，main classes 不在 integrationTest 的 runtime
// classpath 上，@SpringBootTest 找不到 @SpringBootApplication。显式补 sourceSets["main"].output
// 绕过该限制，让 @SpringBootTest 的 package 搜索能定位主类。
sourceSets {
    named("integrationTest") {
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
    named("e2eTest") {
        compileClasspath += sourceSets["main"].output + sourceSets["integrationTest"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["integrationTest"].output + sourceSets["test"].output
    }
}

// ==================== Annotation Processors ====================
// extendsFrom 不传递 annotationProcessor configuration，需要为每个 suite 显式声明
dependencies {
    // Lombok
    compileOnly(libs.findLibrary("lombok").get())
    annotationProcessor(libs.findLibrary("lombok").get())
    testCompileOnly(libs.findLibrary("lombok").get())
    testAnnotationProcessor(libs.findLibrary("lombok").get())
    "integrationTestCompileOnly"(libs.findLibrary("lombok").get())
    "integrationTestAnnotationProcessor"(libs.findLibrary("lombok").get())
    "e2eTestCompileOnly"(libs.findLibrary("lombok").get())
    "e2eTestAnnotationProcessor"(libs.findLibrary("lombok").get())

    // MapStruct
    annotationProcessor(libs.findLibrary("mapstruct-processor").get())
    testAnnotationProcessor(libs.findLibrary("mapstruct-processor").get())
    "integrationTestAnnotationProcessor"(libs.findLibrary("mapstruct-processor").get())
    "e2eTestAnnotationProcessor"(libs.findLibrary("mapstruct-processor").get())

    // Lombok-MapStruct Binding
    annotationProcessor(libs.findLibrary("lombok-mapstruct-binding").get())
    testAnnotationProcessor(libs.findLibrary("lombok-mapstruct-binding").get())
    "integrationTestAnnotationProcessor"(libs.findLibrary("lombok-mapstruct-binding").get())
    "e2eTestAnnotationProcessor"(libs.findLibrary("lombok-mapstruct-binding").get())

    // JUnit Platform Launcher
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "e2eTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
