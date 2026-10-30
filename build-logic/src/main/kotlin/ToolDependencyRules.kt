/// R1 / R2：工具模块之间与工具对业务的 project 依赖规则。
///
/// 纯函数：输入是模块路径与它声明的 `(配置名, 目标模块路径)` 列表，输出违规描述；
/// 插件在配置阶段算好结果作为任务输入，与 configuration cache 兼容。
object ToolDependencyRules {

    const val CORE = ":patra-tool:patra-tool-core"
    const val TEST = ":patra-tool:patra-tool-test"

    /// 工具间允许的硬依赖白名单：security 要用 web 的 ProblemDetail 适配器。
    val HARD_WHITELIST: Map<String, Set<String>> = mapOf(
        ":patra-tool:patra-tool-security" to setOf(":patra-tool:patra-tool-web"),
    )

    private val HARD_CONFIGURATIONS = setOf("api", "implementation", "runtimeOnly", "testFixturesApi", "testFixturesImplementation")

    /// 一条声明的 project 依赖。
    data class Declared(val configuration: String, val target: String)

    /// R1：任何配置都不得指向 `:patra-api` 下的模块。
    fun r1Violations(declared: List<Declared>): List<String> =
        declared.filter { it.target.startsWith(":patra-api") }.map { "${it.configuration} -> ${it.target}" }

    /// R2：工具间硬依赖只允许指向 core（白名单例外）；compileOnly 任意；测试配置可用 core、test 与主代码已 compileOnly 的模块。
    fun r2Violations(project: String, declared: List<Declared>): List<String> {
        val compileOnlyTargets = declared.filter { it.configuration == "compileOnly" }.map { it.target }.toSet()
        val whitelist = HARD_WHITELIST[project].orEmpty()
        return declared
            .filter { it.target.startsWith(":patra-tool:") && it.target != project }
            .filterNot { dependency ->
                val target = dependency.target
                when {
                    dependency.configuration == "compileOnly" -> true
                    dependency.configuration in HARD_CONFIGURATIONS -> target == CORE || target in whitelist
                    isTestConfiguration(dependency.configuration) -> target == CORE || target == TEST || target in compileOnlyTargets
                    else -> true
                }
            }
            .map { "${it.configuration} -> ${it.target}" }
    }

    /// 测试类配置：test*（不含 testFixtures*）、integrationTest*、e2eTest*。
    fun isTestConfiguration(name: String): Boolean =
        (name.startsWith("test") && !name.startsWith("testFixtures")) ||
            name.startsWith("integrationTest") || name.startsWith("e2eTest")
}
