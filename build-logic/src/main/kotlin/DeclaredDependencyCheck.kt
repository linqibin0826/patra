import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/// R5 的 Gradle 任务：配置阶段只收集「声明了哪些 project 依赖、目标模块的源码目录在哪」，
/// 执行阶段才读源码（本模块的 import、目标模块拥有的包），与 configuration cache 兼容。
abstract class DeclaredDependencyCheck : DefaultTask() {
    @get:Input abstract val modulePath: Property<String>

    /// 形如 `configuration|:path` 的声明列表。
    @get:Input abstract val declared: ListProperty<String>

    /// 目标模块路径 → 它的 `src/main/java` 绝对路径。
    @get:Input abstract val targetMainJava: MapProperty<String, String>

    /// 目标模块路径 → 是否带自动配置登记。
    @get:Input abstract val targetAutoConfigured: MapProperty<String, Boolean>

    @get:Input abstract val exempt: SetProperty<String>

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal abstract val mainJava: DirectoryProperty

    @TaskAction
    fun check() {
        val declaredList = declared.get().map { line ->
            val (configuration, target) = line.split("|", limit = 2)
            ToolDependencyRules.Declared(configuration, target)
        }
        val imports = collectImports(mainJava.asFile.orNull)
        val targets = targetMainJava.get().mapValues { (path, dir) ->
            DeclaredDependencyRules.Target(collectPackages(File(dir)), targetAutoConfigured.get()[path] ?: false)
        }
        val violations = DeclaredDependencyRules.violations(declaredList, imports, targets, exempt.get())
        if (violations.isNotEmpty()) {
            throw GradleException(
                "R5 违规：${modulePath.get()}\n" + violations.joinToString("\n") { "  $it" } +
                    "\n主代码没用到的 project 依赖请删掉；只在测试里用的改成测试配置；目标靠自动配置生效的改 runtimeOnly。",
            )
        }
    }

    private fun collectImports(dir: File?): Set<String> {
        if (dir == null || !dir.isDirectory) return emptySet()
        val pattern = Regex("""^\s*import\s+(?:static\s+)?([\w.]+(?:\.\*)?)\s*;""", RegexOption.MULTILINE)
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .flatMap { file -> pattern.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
    }

    private fun collectPackages(dir: File): Set<String> {
        if (!dir.isDirectory) return emptySet()
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .map { it.parentFile.relativeTo(dir).path.replace(File.separatorChar, '.') }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}

private const val IMPORTS_FILE = "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
private const val FACTORIES_FILE = "src/main/resources/META-INF/spring.factories"

/// 给当前项目注册 `checkDeclaredDependencies` 并挂到 `check`；`exempt` 是约定插件自己加的、按层统一给的 project 依赖。
fun Project.registerDeclaredDependencyCheck(exempt: Set<String>) {
    val declaredProjectDependencies = provider {
        configurations.flatMap { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java).map { "${configuration.name}|${it.path}" }
        }.distinct()
    }
    val targetProjects = provider {
        configurations.flatMap { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java).map { it.path }
        }.distinct().associateWith { path -> project(path) }
    }
    val check = tasks.register<DeclaredDependencyCheck>("checkDeclaredDependencies") {
        group = "verification"
        description = "R5：api / implementation 声明的 project 依赖必须在主代码里 import 到"
        modulePath.set(project.path)
        declared.set(declaredProjectDependencies)
        targetMainJava.set(targetProjects.map { targets -> targets.mapValues { (_, p) -> p.file("src/main/java").absolutePath } })
        targetAutoConfigured.set(
            targetProjects.map { targets ->
                targets.mapValues { (_, p) -> p.file(IMPORTS_FILE).isFile || p.file(FACTORIES_FILE).isFile }
            },
        )
        this.exempt.set(exempt)
        mainJava.set(layout.projectDirectory.dir("src/main/java"))
        sourceFiles.from(fileTree("src/main/java"))
        sourceFiles.from(targetProjects.map { targets -> targets.values.map { p -> p.fileTree("src/main/java") } })
    }
    pluginManager.withPlugin("java-base") {
        tasks.named("check") { dependsOn(check) }
    }
}
