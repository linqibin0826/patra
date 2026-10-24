package dev.patra.tool.web.error.probe;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/// 只在测试里存在的接口，用来制造「响应写到一半才失败」的场景。
///
/// 每次写 32 KB：`StreamingResponseBody` 拿到的流 `flush()` 不起作用，要写满 Tomcat 的 8 KB 缓冲，
/// 响应才会真正提交。
@RestController
public class CommittedResponseProbeController {

  /// 一块超过 Tomcat 响应缓冲的数据。
  private static final byte[] CHUNK = "x".repeat(32 * 1024).getBytes(StandardCharsets.US_ASCII);

  /// 写出中途失败时抛出的异常文案。
  private static final String FAILURE = "写出中途失败";

  /// 流式写出三块之后失败：响应早已提交。
  ///
  /// @return 写到一半抛异常的响应体
  @GetMapping("/probe/committed/streaming")
  public StreamingResponseBody streaming() {
    return out -> {
      for (int i = 0; i < 3; i++) {
        out.write(CHUNK);
        out.flush();
      }
      throw new IllegalStateException(FAILURE);
    };
  }

  /// 在请求线程里直接写响应并 flush 之后失败，和网关代理转发的写法一样。
  ///
  /// @param response HTTP 响应
  /// @throws IOException 写出失败
  @GetMapping("/probe/committed/sync")
  public void sync(HttpServletResponse response) throws IOException {
    response.setContentType("text/plain");
    OutputStream out = response.getOutputStream();
    out.write(CHUNK);
    out.flush();
    throw new IllegalStateException(FAILURE);
  }

  /// 流式只写几个字节就失败：数据还在缓冲里，响应没有提交。
  ///
  /// @return 写到一半抛异常的响应体
  @GetMapping("/probe/committed/streaming-small")
  public StreamingResponseBody streamingSmall() {
    return out -> {
      out.write("partial".getBytes(StandardCharsets.US_ASCII));
      out.flush();
      throw new IllegalStateException(FAILURE);
    };
  }
}
