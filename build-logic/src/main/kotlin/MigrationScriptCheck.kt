import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/// R6 的 Gradle 任务：逐个读本模块的 Flyway 脚本交给 MigrationScriptRules 判定；没有脚本的模块直接 NO-SOURCE。
abstract class MigrationScriptCheck : DefaultTask() {
    @get:Input abstract val modulePath: Property<String>

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrationFiles: ConfigurableFileCollection

    @get:Internal abstract val projectDir: DirectoryProperty

    @TaskAction
    fun check() {
        val root = projectDir.asFile.get()
        val violations = migrationFiles.files.sortedBy { it.path }.flatMap { file ->
            MigrationScriptRules.violations(file.readText()).map { "${file.relativeTo(root).path}:${it.line} ${it.message}" }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "R6 违规：${modulePath.get()}\n" + violations.joinToString("\n") { "  $it" } +
                    "\n库表规范见 .claude/rules/tech/database.md。",
            )
        }
    }
}

/// 给当前项目注册 `checkMigrationScripts` 并挂到 `check`。
fun Project.registerMigrationScriptCheck() {
    val check = tasks.register<MigrationScriptCheck>("checkMigrationScripts") {
        group = "verification"
        description = "R6：Flyway 脚本不建 schema、不建外键，username / user_name 列只许出现在 identity 的两张用户名表"
        modulePath.set(project.path)
        migrationFiles.from(fileTree("src/main/resources/db/migration") { include("**/*.sql") })
        projectDir.set(layout.projectDirectory)
    }
    pluginManager.withPlugin("java-base") {
        tasks.named("check") { dependsOn(check) }
    }
}
