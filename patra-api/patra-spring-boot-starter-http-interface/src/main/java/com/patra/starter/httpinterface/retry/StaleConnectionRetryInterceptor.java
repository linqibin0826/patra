package com.patra.starter.httpinterface.retry;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/// Stale Connection 重试拦截器
///
/// 专门处理 JDK HttpClient 的 stale connection 问题。当检测到以下错误时自动重试：
///
/// - `HTTP/1.1 header parser received no bytes`
/// - `EOF reached while reading`
/// - `Unexpected end of file from server`
///
/// **问题背景**：
///
/// JDK HttpClient 的连接池可能复用已被服务端关闭的连接（stale connection），
/// 导致请求失败。这是 HTTP Keep-Alive 机制的固有问题，无法通过配置完全避免。
///
/// **解决方案**：
///
/// 检测到 stale connection 错误时，自动重试请求。由于连接池会建立新连接，
/// 重试通常会成功。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class StaleConnectionRetryInterceptor implements ClientHttpRequestInterceptor {

  /// Stale connection 错误的特征字符串
  private static final String[] STALE_CONNECTION_INDICATORS = {
    "header parser received no bytes",
    "EOF reached while reading",
    "Unexpected end of file",
    "Connection reset",
    "Broken pipe"
  };

  private final int maxRetries;

  /// 创建重试拦截器
  ///
  /// @param maxRetries 最大重试次数（不包括首次请求）
  public StaleConnectionRetryInterceptor(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  /// 默认构造器，使用 2 次重试
  public StaleConnectionRetryInterceptor() {
    this(2);
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

    IOException lastException = null;

    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return execution.execute(request, body);
      } catch (IOException e) {
        if (isStaleConnectionError(e) && attempt < maxRetries) {
          lastException = e;
          log.warn(
              "检测到 stale connection 错误，正在重试 ({}/{}): {} {} - {}",
              attempt + 1,
              maxRetries,
              request.getMethod(),
              request.getURI(),
              extractErrorMessage(e));
        } else {
          throw e;
        }
      }
    }

    // 不应该到达这里，但为了编译器满意
    throw lastException != null ? lastException : new IOException("重试失败");
  }

  /// 判断是否为 stale connection 错误
  private boolean isStaleConnectionError(IOException e) {
    String message = extractErrorMessage(e);
    for (String indicator : STALE_CONNECTION_INDICATORS) {
      if (message.contains(indicator)) {
        return true;
      }
    }
    return false;
  }

  /// 提取完整的错误信息（包括 cause 链）
  private String extractErrorMessage(Throwable e) {
    StringBuilder sb = new StringBuilder();
    Throwable current = e;
    while (current != null) {
      if (current.getMessage() != null) {
        sb.append(current.getMessage()).append(" ");
      }
      current = current.getCause();
    }
    return sb.toString();
  }
}
