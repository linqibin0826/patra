package com.patra.ingest.app.usecase.relay.classifier;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.patra.ingest.domain.exception.OutboxPublishException;
import com.patra.ingest.domain.exception.OutboxRelayExecutionException;
import com.patra.ingest.domain.policy.RelayErrorClassifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 默认异常分类策略: 用于决定 FATAL 和 TRANSIENT 的最小但实用的规则
///
/// 分类规则:
///
/// - 配置、非法状态或参数问题 (IllegalArgument, IllegalState, OutboxRelayExecutionException) → FATAL
///   - JSON 序列化或反序列化失败 → FATAL (数据通常无法恢复)
///   - 所有其他情况 → TRANSIENT 并委托给重试策略 (网络故障、下游中断等)
///
/// 可扩展性: 用组合策略替换此实现以支持更细粒度的匹配 (例如按错误代码)
@Slf4j
@Component
public class RelayErrorClassifierImpl implements RelayErrorClassifier {

  @Override
  public RelayErrorKind classify(Throwable cause) {
    Throwable publish = findPublishException(cause);
    if (publish instanceof OutboxPublishException publishException) {
      RelayErrorKind kind =
          publishException.getReason().isFatal() ? RelayErrorKind.FATAL : RelayErrorKind.TRANSIENT;

      if (log.isDebugEnabled()) {
        log.debug(
            "将 OutboxPublishException 分类为 [{}]: 原因 [{}], isFatal [{}]",
            kind,
            publishException.getReason(),
            publishException.getReason().isFatal());
      }

      return kind;
    }

    Throwable root = ExceptionUtil.getRootCause(cause);
    RelayErrorKind kind;

    if (root instanceof OutboxRelayExecutionException
        || root instanceof IllegalArgumentException
        || root instanceof IllegalStateException
        || root instanceof JsonProcessingException) {
      kind = RelayErrorKind.FATAL;
    } else {
      kind = RelayErrorKind.TRANSIENT;
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "将异常分类为 [{}]: 异常类型 [{}], 根本原因 [{}]",
          kind,
          cause.getClass().getSimpleName(),
          root.getClass().getSimpleName());
    }

    return kind;
  }

  private Throwable findPublishException(Throwable cause) {
    Throwable current = cause;
    while (current != null) {
      if (current instanceof OutboxPublishException) {
        return current;
      }
      current = current.getCause();
    }
    return null;
  }
}
