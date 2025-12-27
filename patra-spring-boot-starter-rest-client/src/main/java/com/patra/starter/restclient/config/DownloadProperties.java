package com.patra.starter.restclient.config;

import com.patra.starter.restclient.download.WriteStrategy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// 下载配置属性。
///
/// 绑定 {@code patra.rest-client.download} 前缀，提供下载目录、写入策略、重试与 FTP 配置。
///
/// @author linqibin
/// @since 0.1.0
@ConfigurationProperties(prefix = "patra.rest-client.download")
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

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Path getBaseDir() {
    return baseDir;
  }

  public void setBaseDir(Path baseDir) {
    this.baseDir = baseDir;
  }

  public Path getTempDir() {
    return tempDir;
  }

  public void setTempDir(Path tempDir) {
    this.tempDir = tempDir;
  }

  public WriteStrategy getWriteStrategy() {
    return writeStrategy;
  }

  public void setWriteStrategy(WriteStrategy writeStrategy) {
    this.writeStrategy = writeStrategy;
  }

  public boolean isCreateDirs() {
    return createDirs;
  }

  public void setCreateDirs(boolean createDirs) {
    this.createDirs = createDirs;
  }

  public boolean isCleanupOnFailure() {
    return cleanupOnFailure;
  }

  public void setCleanupOnFailure(boolean cleanupOnFailure) {
    this.cleanupOnFailure = cleanupOnFailure;
  }

  public int getBufferSize() {
    return bufferSize;
  }

  public void setBufferSize(int bufferSize) {
    this.bufferSize = bufferSize;
  }

  public RetryConfig getRetry() {
    return retry;
  }

  public void setRetry(RetryConfig retry) {
    this.retry = retry;
  }

  public FtpConfig getFtp() {
    return ftp;
  }

  public void setFtp(FtpConfig ftp) {
    this.ftp = ftp;
  }

  /// 重试配置。
  public static class RetryConfig {
    private boolean enabled = true;
    private int maxAttempts = 3;
    private Duration initialBackoff = Duration.ofSeconds(2);
    private Duration maxBackoff = Duration.ofSeconds(30);

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public Duration getInitialBackoff() {
      return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
      this.initialBackoff = initialBackoff;
    }

    public Duration getMaxBackoff() {
      return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
      this.maxBackoff = maxBackoff;
    }
  }

  /// FTP 配置。
  public static class FtpConfig {
    private boolean enabled = true;
    /// FTP 用户名（默认不提供，必须显式配置或在调用时传入）。
    private String username;
    /// FTP 密码（默认不提供，必须显式配置或在调用时传入）。
    private String password;
    private Duration connectTimeout = Duration.ofSeconds(30);
    private Duration dataTimeout = Duration.ofMinutes(30);
    private boolean passiveMode = true;
    private String defaultContentType = "application/xml";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public Duration getConnectTimeout() {
      return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    public Duration getDataTimeout() {
      return dataTimeout;
    }

    public void setDataTimeout(Duration dataTimeout) {
      this.dataTimeout = dataTimeout;
    }

    public boolean isPassiveMode() {
      return passiveMode;
    }

    public void setPassiveMode(boolean passiveMode) {
      this.passiveMode = passiveMode;
    }

    public String getDefaultContentType() {
      return defaultContentType;
    }

    public void setDefaultContentType(String defaultContentType) {
      this.defaultContentType = defaultContentType;
    }
  }
}
