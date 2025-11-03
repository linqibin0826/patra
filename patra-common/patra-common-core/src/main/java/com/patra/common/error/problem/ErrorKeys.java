package com.patra.common.error.problem;

/**
 * 错误处理系统中使用的标准 ProblemDetail 扩展字段键常量。
 *
 * <p>这些键确保 RFC 7807 ProblemDetail 响应中的字段命名一致性。
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class ErrorKeys {

  /** 业务错误码字段键 */
  public static final String CODE = "code";

  /** 用于分布式跟踪关联的 Trace ID 字段键 */
  public static final String TRACE_ID = "traceId";

  /** 原始请求路径字段键 */
  public static final String PATH = "path";

  /** 错误发生时间戳字段键 */
  public static final String TIMESTAMP = "timestamp";

  /** 详细验证错误信息字段键 */
  public static final String ERRORS = "errors";

  private ErrorKeys() {
    // 工具类 - 防止实例化
  }
}
