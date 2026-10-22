import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/// 下载一个固定版本的文件并校验 SHA-256：先下到临时文件，校验通过才原子移到目标位置。
/// 输入是地址与校验和，输出是目标文件，二者不变时 Gradle 认为是最新的、不再下载。
/// 用于测试要用、又不该进 git 的外部数据文件（ip2region 的 xdb，PAP-99）。
abstract class VerifiedDownload : DefaultTask() {
    @get:Input abstract val url: Property<String>

    @get:Input abstract val sha256: Property<String>

    @get:OutputFile abstract val destination: RegularFileProperty

    @TaskAction
    fun download() {
        val target = destination.get().asFile.toPath()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, target.fileName.toString(), ".part")
        try {
            URI(url.get()).toURL().openStream().use { input ->
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING)
            }
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(temporary).use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(sha256.get(), ignoreCase = true)) {
                throw GradleException(
                    "${url.get()} 的 SHA-256 不符：期望 ${sha256.get()}，实际 $actual",
                )
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
