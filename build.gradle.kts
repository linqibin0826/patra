/**
 * Patra - Root Build Script
 *
 * 根项目负责：
 * - 聚合项目信息（version）
 * - 跨模块 JaCoCo 报告聚合（jacoco-report-aggregation plugin）
 *
 * 实际构建逻辑在 build-logic Convention Plugins 中。
 *
 * 常用命令：
 * - ./gradlew clean                                清理所有模块
 * - ./gradlew build                                构建所有模块
 * - ./gradlew test                                 跑所有 unit 测试
 * - ./gradlew integrationTest                      跑所有 IT（需 Docker）
 * - ./gradlew e2eTest                              跑所有 E2E（需 Docker）
 * - ./gradlew testCodeCoverageReport               产出 unit 覆盖率聚合 XML
 * - ./gradlew integrationTestCodeCoverageReport    产出 IT 覆盖率聚合 XML
 * - ./gradlew spotlessApply                        格式化所有代码（PAP-13 启用基线后）
 */

plugins {
    base
    `jacoco-report-aggregation`
    // jacoco-report-aggregation 解析全部子项目运行时依赖，须与子项目共用同一套依赖管理规则
    id("linqibin.dependency-management")
}

// ==================== Project Info ====================
// group 由 build-logic 中的身份 convention plugin 显式设置（linqibin.module-commons / linqibin.module-patra）。
allprojects {
    version = property("patraVersion") as String
}

// ==================== JaCoCo Aggregation ====================
// 把所有 apply 了 jacoco plugin 的子项目纳入聚合
dependencies {
    subprojects {
        plugins.withId("jacoco") {
            jacocoAggregation(this@subprojects)
        }
    }
}

reporting {
    reports {
        register<JacocoCoverageReport>("testCodeCoverageReport") {
            testSuiteName = "test"
        }
        register<JacocoCoverageReport>("integrationTestCodeCoverageReport") {
            testSuiteName = "integrationTest"
        }
        // e2eTest 暂不聚合：3 个 E2E 测试覆盖率信号弱，需要时按相同模式追加
    }
}

// ============================================================================
// CI 受影响单元门控 SSOT：把真实 Gradle 模块依赖图导出为 patra-infra/cd/module-graph.json。
// (Task 3 起) detect-changes.sh 将读它把「变更文件」映射成「受影响测试单元」（含跨服务编译期契约扇出）。
// 改了任何 build.gradle.kts 后必须重跑：./gradlew dumpModuleGraph（CI preflight 用 git diff 守卫陈旧）。
// ============================================================================
tasks.register("dumpModuleGraph") {
    group = "ci"
    description = "导出模块依赖图到 patra-infra/cd/module-graph.json（受影响单元门控 SSOT）"
    notCompatibleWithConfigurationCache("遍历整个 project 依赖图")
    val rootDirFile = rootDir
    doLast {
        val subs = rootProject.subprojects.filter { it.tasks.findByName("check") != null }

        // 1) 正向依赖：project path -> 它直接依赖的 project path 集合
        val forward: Map<String, Set<String>> = subs.associate { p ->
            p.path to p.configurations.flatMap<Configuration, String> { c ->
                c.dependencies.withType(ProjectDependency::class.java).map { dep -> dep.path }
            }.toSet()
        }
        // 2) 反向依赖：被依赖者 -> 依赖它的 project 集合
        val reverse = HashMap<String, MutableSet<String>>()
        forward.forEach { (p, deps) -> deps.forEach { d -> reverse.getOrPut(d) { mutableSetOf() }.add(p) } }
        // 3) 传递依赖者闭包
        fun transitiveDependents(start: String): Set<String> {
            val seen = mutableSetOf<String>()
            val stack = ArrayDeque(reverse[start]?.toList() ?: emptyList())
            while (stack.isNotEmpty()) {
                val n = stack.removeLast()
                if (seen.add(n)) stack.addAll(reverse[n]?.toList() ?: emptyList())
            }
            return seen
        }
        // 4) 物理目录 -> 单元
        fun unitOf(dir: String): String = when {
            dir == "patra-api/patra-registry" || dir.startsWith("patra-api/patra-registry/") -> "registry"
            dir == "patra-api/patra-object-storage" || dir.startsWith("patra-api/patra-object-storage/") -> "object-storage"
            dir == "patra-api/patra-catalog" || dir.startsWith("patra-api/patra-catalog/") -> "catalog"
            dir == "patra-api/patra-ingest" || dir.startsWith("patra-api/patra-ingest/") -> "ingest"
            dir == "patra-api/patra-gateway-boot" -> "gateway"
            else -> "foundation"
        }
        val pathToDir = subs.associate { it.path to it.projectDir.relativeTo(rootDirFile).path.replace('\\', '/') }

        val modules = subs.map { p ->
            val dir = pathToDir.getValue(p.path)
            val selfUnit = unitOf(dir)
            val impacts = (transitiveDependents(p.path).map { unitOf(pathToDir[it] ?: "") } + selfUnit)
                .toSortedSet().toList()
            val tasksList = buildList {
                add("check")
                if (p.file("src/integrationTest").exists()) add("integrationTest")
                if (p.file("src/e2eTest").exists()) add("e2eTest")
            }
            linkedMapOf("dir" to dir, "project" to p.path, "unit" to selfUnit, "impacts" to impacts, "tasks" to tasksList)
        }.sortedBy { it["dir"] as String }

        val root = linkedMapOf(
            "_comment" to "dumpModuleGraph 产物，勿手改。受影响单元门控 SSOT。改 build 文件后重跑 ./gradlew dumpModuleGraph。",
            "units" to listOf("registry", "object-storage", "catalog", "ingest", "gateway", "foundation"),
            "modules" to modules
        )
        val json = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(root)) + "\n"
        file("patra-infra/cd/module-graph.json").writeText(json)
        logger.lifecycle("✓ 写出 patra-infra/cd/module-graph.json（${modules.size} 个模块）")
    }
}
