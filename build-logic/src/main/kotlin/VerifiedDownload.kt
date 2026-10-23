import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
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
/// 输入是地址与校验和，输出是目标文件，二者不变时 Gradle 认为是最新的、不再下载；目标文件已在且校验和对得上
/// （比如放在 Gradle 用户目录的缓存里、换了检出或没有任务历史）也直接跳过。连接 30 秒、读取 60 秒无数据即失败。
/// 用于测试要用、又不该进 git 的外部数据文件（ip2region 的 xdb，PAP-99）。
abstract class VerifiedDownload : DefaultTask() {
    @get:Input abstract val url: Property<String>

    @get:Input abstract val sha256: Property<String>

    @get:OutputFile abstract val destination: RegularFileProperty

    @TaskAction
    fun download() {
        val target = destination.get().asFile.toPath()
        if (Files.isRegularFile(target) && sha256Of(target).equals(sha256.get(), ignoreCase = true)) {
            logger.info("{} 已是期望的版本，跳过下载", target)
            return
        }
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, target.fileName.toString(), ".part")
        try {
            val connection = URI(url.get()).toURL().openConnection()
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.getInputStream().use { input ->
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING)
            }
            val actual = sha256Of(temporary)
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

    private fun sha256Of(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 60_000
    }
}
