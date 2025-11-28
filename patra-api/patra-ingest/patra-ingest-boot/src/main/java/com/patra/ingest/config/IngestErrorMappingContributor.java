package com.patra.ingest.config;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.ingest.api.error.IngestErrorCode;
import com.patra.ingest.domain.exception.IngestConfigurationException;
import com.patra.ingest.domain.exception.IngestScheduleParameterException;
import com.patra.ingest.domain.exception.OutboxPersistenceException;
import com.patra.ingest.domain.exception.OutboxRelayExecutionException;
import com.patra.ingest.domain.exception.PlanAssemblyException;
import com.patra.ingest.domain.exception.PlanPersistenceException;
import com.patra.ingest.domain.exception.PlanValidationException;
import com.patra.ingest.domain.exception.TaskCheckpointException;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.StandardErrorTrait;
import com.patra.starter.feign.error.exception.RemoteCallException;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/// 采集服务错误码映射贡献器。
///
/// 实现 {@link ErrorMappingContributor} SPI,注册采集服务特定的领域异常到标准错误码的映射关系,使平台错误解析引擎能够将领域异常转换为一致的 API
/// 错误响应。
///
/// **职责**:
///
/// - 映射配置相关异常(IngestConfigurationException)到相应错误码
/// - 映射调度参数异常(IngestScheduleParameterException、OutboxRelayExecutionException)
/// - 映射检查点异常(TaskCheckpointException)根据类型区分解析/序列化错误
/// - 映射计划相关异常(PlanAssemblyException、PlanPersistenceException)
/// - 映射 Outbox 持久化异常(OutboxPersistenceException)根据阶段区分错误类型
/// - 处理远程调用异常(RemoteCallException)根据 HTTP 状态码细分错误
///
/// **优先级**: 50（中等优先级）- 业务特定异常,在基础设施异常之后处理。
///
/// **错误码体系**: 所有错误码定义在 {@link IngestErrorCode} 枚举中,遵循 ING_xxxx 命名约定(如 ING_1201、ING_1401)。
///
/// **设计模式**: SPI 贡献者模式 - 通过 Spring 组件扫描自动注册到平台错误处理框架。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@Order(50)
public class IngestErrorMappingContributor implements ErrorMappingContributor {

  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    Optional<ErrorCodeLike> errorCode =
        tryMapConfigurationException(exception)
            .or(() -> tryMapScheduleException(exception))
            .or(() -> tryMapCheckpointException(exception))
            .or(() -> tryMapPlanException(exception))
            .or(() -> tryMapOutboxException(exception));

    if (errorCode.isEmpty()) {
      log.debug("未找到异常类型的错误码映射: {}", exception.getClass().getName());
    } else {
      log.debug("已将异常 {} 映射到错误码: {}", exception.getClass().getSimpleName(), errorCode.get().code());
    }

    return errorCode;
  }

  /// 将配置相关异常映射到错误码。
  ///
  /// @param exception 尝试映射的可抛出对象
  /// @return 如果异常类型匹配则返回可选的错误码
  private Optional<ErrorCodeLike> tryMapConfigurationException(Throwable exception) {
    if (exception instanceof IngestConfigurationException configurationException) {
      return Optional.of(resolveConfigurationError(configurationException));
    }
    return Optional.empty();
  }

  /// 将调度参数异常映射到错误码。
  ///
  /// @param exception 尝试映射的可抛出对象
  /// @return 如果异常类型匹配则返回可选的错误码
  private Optional<ErrorCodeLike> tryMapScheduleException(Throwable exception) {
    if (exception instanceof IngestScheduleParameterException) {
      return Optional.of(IngestErrorCode.ING_1401);
    }
    if (exception instanceof OutboxRelayExecutionException) {
      return Optional.of(IngestErrorCode.ING_1402);
    }
    if (exception instanceof PlanValidationException) {
      return Optional.of(IngestErrorCode.ING_1403);
    }
    return Optional.empty();
  }

  /// 将检查点相关异常映射到错误码。
  ///
  /// @param exception 尝试映射的可抛出对象
  /// @return 如果异常类型匹配则返回可选的错误码
  private Optional<ErrorCodeLike> tryMapCheckpointException(Throwable exception) {
    if (exception instanceof TaskCheckpointException checkpointException) {
      if (checkpointException.getType() == TaskCheckpointException.Type.PARSE) {
        return Optional.of(IngestErrorCode.ING_1501);
      }
      if (checkpointException.getType() == TaskCheckpointException.Type.SERIALIZE) {
        return Optional.of(IngestErrorCode.ING_1502);
      }
    }
    return Optional.empty();
  }

  /// 将计划相关异常映射到错误码。
  ///
  /// @param exception 尝试映射的可抛出对象
  /// @return 如果异常类型匹配则返回可选的错误码
  private Optional<ErrorCodeLike> tryMapPlanException(Throwable exception) {
    if (exception instanceof PlanAssemblyException) {
      return Optional.of(IngestErrorCode.ING_1601);
    }
    if (exception instanceof PlanPersistenceException persistenceException) {
      return Optional.of(resolvePlanPersistence(persistenceException));
    }
    return Optional.empty();
  }

  /// 将 outbox 相关异常映射到错误码。
  ///
  /// @param exception 尝试映射的可抛出对象
  /// @return 如果异常类型匹配则返回可选的错误码
  private Optional<ErrorCodeLike> tryMapOutboxException(Throwable exception) {
    if (exception instanceof OutboxPersistenceException persistenceException) {
      return Optional.of(resolveOutboxPersistence(persistenceException));
    }
    return Optional.empty();
  }

  /// 基于远程调用异常的错误特征解析配置错误。
  ///
  /// 使用 {@link RemoteCallException#getErrorTraits()} 进行语义判断，
  /// 根据下游服务传播的 traits 信息确定错误类型。
  ///
  /// @param exception 要分析的配置异常
  /// @return 基于错误特征的适当错误码
  private ErrorCodeLike resolveConfigurationError(IngestConfigurationException exception) {
    Throwable cause = exception.getCause();
    if (cause instanceof RemoteCallException remote) {
      Set<ErrorTrait> traits = remote.getErrorTraits();
      if (traits.contains(StandardErrorTrait.NOT_FOUND)) {
        return IngestErrorCode.ING_1201;
      }
      if (traits.contains(StandardErrorTrait.DEP_UNAVAILABLE)
          || traits.contains(StandardErrorTrait.TIMEOUT)
          || traits.contains(StandardErrorTrait.QUOTA_EXCEEDED)) {
        return IngestErrorCode.ING_1203;
      }
    }
    return IngestErrorCode.ING_1202;
  }

  /// 基于操作阶段解析 outbox 持久化错误。
  ///
  /// @param exception 包含阶段信息的 outbox 持久化异常
  /// @return 基于失败的持久化阶段的适当错误码
  private ErrorCodeLike resolveOutboxPersistence(OutboxPersistenceException exception) {
    return switch (exception.getStage()) {
      case MARK_PUBLISHED -> IngestErrorCode.ING_1302;
      case MARK_RETRY -> IngestErrorCode.ING_1301;
      case MARK_DEAD -> IngestErrorCode.ING_1303;
      case BATCH_INSERT -> IngestErrorCode.ING_1304;
    };
  }

  /// 基于操作阶段解析计划持久化错误。
  ///
  /// @param exception 包含阶段信息的计划持久化异常
  /// @return 基于失败的持久化阶段的适当错误码
  private ErrorCodeLike resolvePlanPersistence(PlanPersistenceException exception) {
    return switch (exception.getStage()) {
      case SCHEDULE_INSTANCE, PLAN, PLAN_SLICE, TASK, TASK_RETRY -> IngestErrorCode.ING_1503;
    };
  }
}
