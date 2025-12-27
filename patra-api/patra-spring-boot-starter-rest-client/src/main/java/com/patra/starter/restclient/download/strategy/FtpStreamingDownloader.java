package com.patra.starter.restclient.download.strategy;

import com.patra.common.error.trait.StandardErrorTrait;
import com.patra.starter.restclient.config.DownloadProperties;
import com.patra.starter.restclient.download.DownloadException;
import com.patra.starter.restclient.download.DownloadOptions;
import com.patra.starter.restclient.download.FtpCredentials;
import com.patra.starter.restclient.download.StreamingDownloadResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

/// FTP 流式下载策略。
///
/// 使用 Apache Commons Net FTPClient 实现流式下载，支持账号密码与超时配置。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class FtpStreamingDownloader implements StreamingDownloader {

  private final DownloadProperties properties;
  // FTP 文件不存在的响应码（RFC 959）
  private static final int FTP_FILE_UNAVAILABLE = 550;

  /// 创建 FTP 流式下载策略。
  ///
  /// @param properties 下载配置
  public FtpStreamingDownloader(DownloadProperties properties) {
    this.properties = properties;
  }

  @Override
  public boolean supports(URI url) {
    if (url == null || url.getScheme() == null) {
      return false;
    }
    return "ftp".equalsIgnoreCase(url.getScheme());
  }

  @Override
  public StreamingDownloadResponse openStream(URI url, DownloadOptions options) {
    Objects.requireNonNull(url, "下载 URL 不能为 null");
    log.info("开始 FTP 下载：{}", url);

    var ftpConfig = properties.getFtp();
    FTPClient ftpClient = new FTPClient();
    ftpClient.setConnectTimeout((int) ftpConfig.getConnectTimeout().toMillis());
    ftpClient.setDataTimeout(ftpConfig.getDataTimeout());

    try {
      String host = url.getHost();
      int port = url.getPort() > 0 ? url.getPort() : 21;
      ftpClient.connect(host, port);

      int replyCode = ftpClient.getReplyCode();
      if (!FTPReply.isPositiveCompletion(replyCode)) {
        throw new DownloadException(
            "FTP 连接被拒绝，响应码：" + replyCode, StandardErrorTrait.DEP_UNAVAILABLE);
      }

      FtpCredentials credentials = resolveCredentials(options, ftpConfig);
      if (!ftpClient.login(credentials.username(), credentials.password())) {
        throw new DownloadException("FTP 登录失败", StandardErrorTrait.DEP_UNAVAILABLE);
      }

      if (ftpConfig.isPassiveMode()) {
        ftpClient.enterLocalPassiveMode();
      }
      ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

      String remotePath = url.getPath();
      log.debug("FTP 连接成功：{}，下载路径：{}", host, remotePath);

      var ftpFile = ftpClient.mlistFile(remotePath);
      long fileSize = ftpFile != null ? ftpFile.getSize() : -1;

      InputStream ftpInputStream = ftpClient.retrieveFileStream(remotePath);
      if (ftpInputStream == null) {
        int fileReplyCode = ftpClient.getReplyCode();
        StandardErrorTrait trait =
            fileReplyCode == FTP_FILE_UNAVAILABLE
                ? StandardErrorTrait.NOT_FOUND
                : StandardErrorTrait.DEP_UNAVAILABLE;
        throw new DownloadException(
            "无法获取 FTP 文件流：" + remotePath + "，响应：" + ftpClient.getReplyString(), trait);
      }

      InputStream wrappedStream = new FtpInputStreamWrapper(ftpInputStream, ftpClient);
      return new StreamingDownloadResponse(
          wrappedStream, fileSize, ftpConfig.getDefaultContentType());

    } catch (DownloadException e) {
      disconnectFtpQuietly(ftpClient);
      throw e;
    } catch (IOException e) {
      disconnectFtpQuietly(ftpClient);
      log.error("FTP 下载 IO 错误：{}", url, e);
      throw new DownloadException(
          "FTP 下载失败：" + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
    } catch (Exception e) {
      disconnectFtpQuietly(ftpClient);
      log.error("FTP 下载未知错误：{}", url, e);
      throw new DownloadException(
          "FTP 下载失败：" + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
    }
  }

  private FtpCredentials resolveCredentials(
      DownloadOptions options, DownloadProperties.FtpConfig ftpConfig) {
    FtpCredentials credentials = options != null ? options.ftpCredentials() : null;
    if (credentials == null) {
      credentials = new FtpCredentials(ftpConfig.getUsername(), ftpConfig.getPassword());
    }
    if (credentials.username() == null
        || credentials.username().isBlank()
        || credentials.password() == null
        || credentials.password().isBlank()) {
      throw new DownloadException(
          "FTP 账号密码未配置，请在 patra.rest-client.download.ftp.* 或 DownloadOptions 中提供",
          StandardErrorTrait.RULE_VIOLATION);
    }
    return credentials;
  }

  private void disconnectFtpQuietly(FTPClient ftpClient) {
    if (ftpClient.isConnected()) {
      try {
        ftpClient.logout();
        ftpClient.disconnect();
      } catch (IOException e) {
        log.warn("断开 FTP 连接失败", e);
      }
    }
  }

  /// FTP InputStream 包装器。
  ///
  /// 确保关闭 InputStream 时同时完成 FTP 传输并断开连接。
  private static class FtpInputStreamWrapper extends InputStream {
    private final InputStream delegate;
    private final FTPClient ftpClient;

    FtpInputStreamWrapper(InputStream delegate, FTPClient ftpClient) {
      this.delegate = delegate;
      this.ftpClient = ftpClient;
    }

    @Override
    public int read() throws IOException {
      return delegate.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return delegate.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
      try {
        delegate.close();
        if (!ftpClient.completePendingCommand()) {
          log.warn("FTP completePendingCommand 失败");
        }
      } finally {
        if (ftpClient.isConnected()) {
          try {
            ftpClient.logout();
            ftpClient.disconnect();
          } catch (IOException e) {
            log.warn("断开 FTP 连接失败", e);
          }
        }
      }
    }
  }
}
