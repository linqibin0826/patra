/// R5：声明的 project 依赖必须被使用（spec 4.6 退回方案，即盘点 G5 的算法）。
///
/// 对 `api` / `implementation` 声明的每条 project 依赖，要求本模块 `src/main/java` 至少有一个 `import`
/// 落在目标模块 `src/main/java` 实际拥有的包里；目标带自动配置登记（`AutoConfiguration.imports` /
/// `spring.factories`）的视为「靠自动配置生效」放行。第三方构件、compileOnly / runtimeOnly / 测试配置不查。
object DeclaredDependencyRules {

    /// 目标模块的事实：它拥有的包、是否带自动配置登记。
    data class Target(val packages: Set<String>, val autoConfigured: Boolean)

    private val HARD_CONFIGURATIONS = setOf("api", "implementation")

    /// 返回违规描述，空表示通过。`imports` 是本模块主代码全部 import 的限定名（`import static a.b.C.m` 记作 `a.b.C.m`，`a.b.*` 记作 `a.b.*`）。
    fun violations(
        declared: List<ToolDependencyRules.Declared>,
        imports: Set<String>,
        targets: Map<String, Target>,
        exempt: Set<String>,
    ): List<String> =
        declared
            .filter { it.configuration in HARD_CONFIGURATIONS && it.target.startsWith(":") && it.target !in exempt }
            .filter { dependency ->
                val target = targets[dependency.target] ?: return@filter false
                !target.autoConfigured && target.packages.none { pkg -> imports.any { it.startsWith("$pkg.") } }
            }
            .map { "${it.configuration} -> ${it.target}（src/main/java 没有 import 它的任何包）" }
}
