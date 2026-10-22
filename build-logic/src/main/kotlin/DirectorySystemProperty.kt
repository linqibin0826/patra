import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/// 把一个目录作为系统属性传给测试 JVM，同时登记成任务输入：目录里的文件变了测试不会被当成最新的，
/// 产出这个目录的任务（`FileCollection` 带的构建依赖）自动排在测试之前。用于 ip2region 数据目录（PAP-99）。
class DirectorySystemProperty(
    @get:Input val name: String,
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) val directory: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("-D$name=${directory.singleFile.absolutePath}")
}
