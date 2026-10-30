/// R3：domain 模块编译类路径白名单（决策 C）。
object DomainPurityRules {

    /// 允许出现在 *-domain compileClasspath 上的构件（group:name）；JDK 不是构件，天然允许。
    val ALLOWED: Set<String> = setOf(
        "dev.patra:patra-tool-core",
        "org.projectlombok:lombok",
        "com.fasterxml.jackson.core:jackson-annotations",
        "tools.jackson.core:jackson-core",
        "tools.jackson.core:jackson-databind",
        "cn.hutool:hutool-core",
        "org.mapstruct:mapstruct",
    )

    /// 返回名单外的构件，排序后便于比对。
    fun violations(artifacts: Collection<String>): List<String> = artifacts.filterNot { it in ALLOWED }.distinct().sorted()
}
