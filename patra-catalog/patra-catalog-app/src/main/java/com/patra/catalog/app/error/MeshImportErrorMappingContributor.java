package com.patra.catalog.app.error;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.model.SimpleErrorCode;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/// MeSH 导入业务异常映射贡献者。
///
/// 将标准异常类型映射到特定的业务错误码和 HTTP 状态码：
///
/// - {@link IllegalStateException} → 409 Conflict（业务状态冲突，如已有任务运行）
///   - {@link IllegalArgumentException}（消息包含"任务不存在"） → 404 Not Found
///   - {@link IllegalArgumentException}（其他） → 400 Bad Request（参数错误）
///
/// **设计理念**：
///
/// - 不创建自定义异常类，复用 JDK 标准异常（简单性原则）
///   - 通过异常消息内容区分业务语义（实用主义）
///   - 集中管理异常到 HTTP 状态码的映射（DRY 原则）
///
/// **优先级**： 40（高优先级） - 处理通用异常（IllegalStateException、IllegalArgumentException）的业务语义映射，需要高于框架默认的类名启发式（IllegalXxxException → 422），确保业务语义正确映射。
///
/// **错误码格式**： CATALOG-{httpStatusCode}，例如：
///
/// - CATALOG-0409（业务状态冲突）
///   - CATALOG-0404（资源不存在）
///   - CATALOG-0400（参数错误）
///
/// **错误消息**：从异常 {@link Throwable#getMessage()} 中自动提取，无需在 ErrorCode 中重复定义。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@Order(40)
public class MeshImportErrorMappingContributor implements ErrorMappingContributor {

  private static final String CONTEXT_PREFIX = "CATALOG";

  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    // 1. 处理 IllegalStateException → 409 Conflict
    if (exception instanceof IllegalStateException ex) {
      log.debug("映射 IllegalStateException 到 409 Conflict: {}", ex.getMessage());
      return Optional.of(SimpleErrorCode.create(CONTEXT_PREFIX, "0409"));
    }

    // 2. 处理 IllegalArgumentException
    if (exception instanceof IllegalArgumentException ex) {
      String message = ex.getMessage();

      // 2.1 消息包含"任务不存在" → 404 Not Found
      if (message != null && message.contains("任务不存在")) {
        log.debug("映射 IllegalArgumentException（任务不存在）到 404 Not Found: {}", message);
        return Optional.of(SimpleErrorCode.create(CONTEXT_PREFIX, "0404"));
      }

      // 2.2 其他 IllegalArgumentException → 400 Bad Request
      log.debug("映射 IllegalArgumentException 到 400 Bad Request: {}", message);
      return Optional.of(SimpleErrorCode.create(CONTEXT_PREFIX, "0400"));
    }

    // 传递给下一个贡献者或默认映射
    return Optional.empty();
  }
}
