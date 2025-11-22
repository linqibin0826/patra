package com.patra.starter.web.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.ProblemDetail;

/// 控制 Web 层错误响应的配置属性（例如 {@link ProblemDetail}）。
/// 
/// @author linqibin
/// @since 0.1.0
@Data
@ConfigurationProperties(prefix = "patra.web.problem")
public class WebErrorProperties {

  /// 是否启用 Web 特定的错误处理。
  private boolean enabled = true;

  /// 用于构建 `ProblemDetail#type` 属性的基础 URL。
  private String typeBaseUrl = "https://errors.example.com/";

  /// 是否在响应中包含堆栈跟踪（仅用于调试目的）。
  private boolean includeStack = false;
}
