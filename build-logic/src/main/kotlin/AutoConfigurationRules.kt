import java.io.File

/// R4：自动配置写法检查（spec 4.5 的规则 a–g），按源码文本判定，不依赖编译产物。
object AutoConfigurationRules {

    /// 一个工具模块的输入：三个目录 / 文件可以不存在（纯 Java 模块没有自动配置）。
    data class Input(
        val mainJava: File?,
        val importsFile: File?,
        val testJava: File?,
        val packageSegment: String,
        val classPrefix: String,
        val configPrefix: String,
    )

    private val STEREOTYPE = Regex("""^\s*@(Component|Service|Repository)\b""")
    private val CONFIGURATION = Regex("""^\s*@Configuration\b""")
    private val AUTO_CONFIGURATION = Regex("""^\s*@AutoConfiguration\b""")
    private val BARE_ORDER = Regex("""@Order\(\s*-?\d""")
    private val MAPPER_BUILD = Regex("""JsonMapper\.builder\(\)|XmlMapper\.builder\(\)|new ObjectMapper\(|new JsonMapper\(""")
    private val CONFIG_PROPERTIES = Regex("""@ConfigurationProperties\b""")
    private val CONFIG_PREFIX = Regex("""@ConfigurationProperties\s*\(\s*(?:prefix\s*=\s*)?"([^"]+)"""")
    private val PACKAGE = Regex("""^package\s+([\w.]+)\s*;""", RegexOption.MULTILINE)
    private val PUBLIC_FINAL_CLASS = Regex("""^public\s+final\s+class\s+(\w+)""", RegexOption.MULTILINE)
    private val TOP_LEVEL_TYPE = Regex("""^(?:public\s+)?(?:final\s+|abstract\s+)*(?:class|interface|enum|record)\s+(\w+)""", RegexOption.MULTILINE)
    private val RECORD = Regex("""\brecord\s+\w+\s*\(""")

    /// 返回违规描述列表，空表示通过。
    fun check(input: Input): List<String> {
        val problems = mutableListOf<String>()
        val autoConfigurePackage = "dev.patra.tool.${input.packageSegment}.autoconfigure"
        val mainJava = input.mainJava?.takeIf { it.isDirectory }
        val sources = mainJava?.walkTopDown()?.filter { it.isFile && it.extension == "java" }?.sortedBy { it.path }?.toList().orEmpty()
        val imports = input.importsFile?.takeIf { it.isFile }?.readLines()
            ?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") }.orEmpty()
        val declared = mutableSetOf<String>()

        for (file in sources) {
            val rel = file.relativeTo(mainJava!!).path
            val code = stripComments(file.readText())
            val pkg = PACKAGE.find(code)?.groupValues?.get(1).orEmpty()
            val inAutoConfigure = pkg == autoConfigurePackage
            code.lines().forEachIndexed { index, line ->
                val no = index + 1
                if (STEREOTYPE.containsMatchIn(line)) problems += "$rel:$no 类上有 ${line.trim()}（规则 c：工具模块不用构造型注解）"
                if (!inAutoConfigure && CONFIGURATION.containsMatchIn(line)) problems += "$rel:$no @Configuration 只能出现在 $autoConfigurePackage（规则 d）"
                if (BARE_ORDER.containsMatchIn(line)) problems += "$rel:$no 裸数字 @Order，改用 PatraOrders 常量（规则 e）"
                if (!inAutoConfigure && MAPPER_BUILD.containsMatchIn(line)) problems += "$rel:$no autoconfigure 包以外不得自建 Jackson mapper（规则 f）"
            }
            if (CONFIG_PROPERTIES.containsMatchIn(code)) {
                if (!inAutoConfigure) problems += "$rel @ConfigurationProperties 只能出现在 $autoConfigurePackage（规则 g）"
                if (!RECORD.containsMatchIn(code)) problems += "$rel 配置类必须是 record（规则 g）"
                val prefix = CONFIG_PREFIX.find(code)?.groupValues?.get(1)
                val ok = prefix != null && (prefix == input.configPrefix || prefix.startsWith(input.configPrefix + "."))
                if (!ok) problems += "$rel 配置前缀必须是 ${input.configPrefix}，实际 ${prefix ?: "未写"}（规则 g）"
            }
            if (code.lines().any { AUTO_CONFIGURATION.containsMatchIn(it) }) {
                val name = TOP_LEVEL_TYPE.find(code)?.groupValues?.get(1) ?: file.nameWithoutExtension
                val fqn = "$pkg.$name"
                declared += fqn
                if (!inAutoConfigure) problems += "$rel 自动配置类必须在 $autoConfigurePackage（规则 a）"
                if (PUBLIC_FINAL_CLASS.find(code)?.groupValues?.get(1) != name) problems += "$rel 自动配置类必须声明为 public final class（规则 a）"
                if (!Regex("^${input.classPrefix}\\w*AutoConfiguration$").matches(name)) problems += "$rel 自动配置类名必须形如 ${input.classPrefix}<Aspect>AutoConfiguration（规则 a）"
                if (fqn !in imports) problems += "$rel 没有登记在 AutoConfiguration.imports（规则 a）"
                val testPath = "${pkg.replace('.', '/')}/${name}Test.java"
                if (input.testJava?.resolve(testPath)?.isFile != true) problems += "$rel 缺少契约测试 src/test/java/$testPath（规则 a）"
            }
        }
        for (fqn in imports) {
            if (fqn !in declared) problems += "imports 登记了 $fqn，但源码里没有这个带 @AutoConfiguration 的类（规则 b）"
        }
        return problems
    }

    /// 去掉块注释与行注释（含 `///` Javadoc），字符串里的 `//` 也会被截掉，对本检查无影响。
    fun stripComments(code: String): String =
        code.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.replace(Regex("""//.*$"""), "") }
}
