package dev.linqibin.starter.restclient.config;

import dev.linqibin.starter.restclient.download.WriteStrategy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// 下载配置属性。
///
/// 绑定 {@code linqibin.starter.rest-client.download} 前缀，提供下载目录、写入策略、重试与 FTP 配置。
///
/// @author linqibin
/// @since 0.1.0
@ConfigurationProperties(prefix = "linqibin.starter.rest-client.download")
@Data
public class DownloadProperties {

  /// 是否启用下载能力（默认 true）。
  private boolean enabled = true;

  /// 默认下载目录（为空时要求调用方显式指定路径）。
  private Path baseDir = Paths.get(System.getProperty("java.io.tmpdir"), "patra-downloads");

  /// 临时目录（为空时使用系统临时目录）。
  private Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));

  /// 默认写入策略。
  private WriteStrategy writeStrategy = WriteStrategy.OVERWRITE;

  /// 是否自动创建目录。
  private boolean createDirs = true;

  /// 下载失败时是否清理文件。
  private boolean cleanupOnFailure = true;

  /// 读写缓冲区大小（默认 64KB）。
  private int bufferSize = 65536;

  /// 重试配置。
  private RetryConfig retry = new RetryConfig();

  /// FTP 配置。
  private FtpConfig ftp = new FtpConfig();

  /// 重试配置。
  @Data
  public static class RetryConfig {

    /// 是否启用重试（默认 true）。
    private boolean enabled = true;

    /// 最大重试次数（默认 3）。
    private int maxAttempts = 3;

    /// 初始退避时间（默认 2 秒）。
    private Duration initialBackoff = Duration.ofSeconds(2);

    /// 最大退避时间（默认 30 秒）。
    private Duration maxBackoff = Duration.ofSeconds(30);
  }

  /// FTP 配置。
  @Data
  public static class FtpConfig {

    /// 是否启用 FTP 下载（默认 true）。
    private boolean enabled = true;

    /// FTP 用户名（默认不提供，必须显式配置或在调用时传入）。
    private String username;

    /// FTP 密码（默认不提供，必须显式配置或在调用时传入）。
    private String password;

    /// 连接超时时间（默认 30 秒）。
    private Duration connectTimeout = Duration.ofSeconds(30);

    /// 数据传输超时时间（默认 30 分钟）。
    private Duration dataTimeout = Duration.ofMinutes(30);

    /// 是否使用被动模式（默认 true）。
    private boolean passiveMode = true;

    /// 默认内容类型（默认 application/xml）。
    private String defaultContentType = "application/xml";
  }
}
