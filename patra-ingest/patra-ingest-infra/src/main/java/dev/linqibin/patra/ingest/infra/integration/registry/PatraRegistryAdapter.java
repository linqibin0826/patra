package dev.linqibin.patra.ingest.infra.integration.registry;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import dev.linqibin.patra.ingest.domain.exception.IngestConfigurationException;
import dev.linqibin.patra.ingest.domain.model.enums.OperationCode;
import dev.linqibin.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import dev.linqibin.patra.ingest.domain.port.PatraRegistryPort;
import dev.linqibin.patra.ingest.infra.integration.registry.converter.ProvenanceConfigSnapshotConverter;
import dev.linqibin.commons.error.remote.RemoteCallException;
import dev.linqibin.commons.error.remote.RemoteErrorHelper;
import dev.linqibin.commons.error.trait.ErrorTrait;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.registry.api.dto.provenance.ProvenanceConfigResp;
import dev.linqibin.patra.registry.api.endpoint.ProvenanceEndpoint;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 出站适配器,调用 patra-registry 获取溯源配置。
///
/// 遵循六边形架构:基础设施组件实现应用端口。从注册服务检索完整的溯源配置快照,具有健壮的错误处理。
///
/// 亮点: - 全面的错误处理和日志记录 - 使用 MapStruct 进行类型安全转换 - 通过返回最小可用快照实现优雅降级
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PatraRegistryAdapter implements PatraRegistryPort {

  /// 注册中心 RPC 客户端。
  private final ProvenanceEndpoint provenanceEndpoint;

  /// 配置快照转换器。
  private final ProvenanceConfigSnapshotConverter converter;

  /// 调用注册中心获取溯源配置。
  @Override
  public ProvenanceConfigSnapshot fetchConfig(
      ProvenanceCode provenanceCode, OperationCode operationCode) {
    String code = provenanceCode.getCode();
    String operationType = operationCode.name();

    try {
      ProvenanceConfigResp resp = callRegistry(provenanceCode, operationType);
      return convertAndValidateResponse(resp, code);
    } catch (RemoteCallException ex) {
      return handleRemoteException(ex, code, operationType);
    } catch (Exception ex) {
      throw handleUnexpectedException(ex, code, operationType);
    }
  }

  /// 调用注册服务检索配置。
  private ProvenanceConfigResp callRegistry(ProvenanceCode provenanceCode, String operationType) {
    TimeInterval timer = DateUtil.timer();
    ProvenanceConfigResp resp =
        provenanceEndpoint.getConfiguration(provenanceCode, operationType, Instant.now());
    if (log.isDebugEnabled()) {
      log.debug(
          "已加载溯源配置 code [{}] operation [{}] in {}ms",
          provenanceCode.getCode(),
          operationType,
          timer.interval());
    }
    return resp;
  }

  /// 转换并验证注册中心响应。
  private ProvenanceConfigSnapshot convertAndValidateResponse(
      ProvenanceConfigResp resp, String code) {
    if (resp == null) {
      log.warn("注册中心返回空配置, code={}", code);
      return createMinimalSnapshot(code);
    }

    return converter.convert(resp);
  }

  /// 处理配置检索期间的意外异常。
  private IngestConfigurationException handleUnexpectedException(
      Exception ex, String code, String operationType) {
    String msg = String.format("获取配置时发生意外错误, code=%s, operationType=%s", code, operationType);
    log.error(msg, ex);
    return new IngestConfigurationException(code, operationType, msg, ex);
  }

  /// 处理远程 ProblemDetail 异常。
  ///
  /// 错误判断策略：优先使用 ErrorTrait 语义判断，HTTP 状态码作为 fallback。
  /// 这种方式更稳定，因为 ErrorTrait 是语义化的，不依赖 HTTP 状态码约定。
  private ProvenanceConfigSnapshot handleRemoteException(
      RemoteCallException ex, String code, String operationType) {
    Set<ErrorTrait> traits = ex.getErrorTraits();

    // 优先使用 ErrorTrait 语义判断
    if (traits.contains(StandardErrorTrait.NOT_FOUND)) {
      throw createConfigNotFoundException(ex, code, operationType);
    }
    if (traits.contains(StandardErrorTrait.DEP_UNAVAILABLE)
        || traits.contains(StandardErrorTrait.TIMEOUT)) {
      return handleRegistryUnavailable(ex, code);
    }

    // Fallback：使用 HTTP 状态码判断（兼容未携带 ErrorTrait 的响应）
    if (RemoteErrorHelper.isNotFound(ex)) {
      throw createConfigNotFoundException(ex, code, operationType);
    }
    if (RemoteErrorHelper.isServerError(ex) || RemoteErrorHelper.isRetryable(ex)) {
      return handleRegistryUnavailable(ex, code);
    }

    throw createClientErrorException(ex, code, operationType);
  }

  /// 为配置未找到错误创建异常。
  private IngestConfigurationException createConfigNotFoundException(
      RemoteCallException ex, String code, String operationType) {
    String msg = String.format("未找到溯源配置, code=%s, operationType=%s", code, operationType);
    log.warn(
        "{} (remoteCode={}, status={}, traceId={})",
        msg,
        ex.getErrorCode(),
        ex.getHttpStatus(),
        ex.getTraceId());
    return new IngestConfigurationException(code, operationType, msg, ex);
  }

  /// 通过返回最小快照来处理注册中心不可用。
  private ProvenanceConfigSnapshot handleRegistryUnavailable(RemoteCallException ex, String code) {
    log.warn(
        "注册中心不可用,降级到最小快照, code={}, status={}, traceId={}",
        code,
        ex.getHttpStatus(),
        ex.getTraceId());
    return createMinimalSnapshot(code);
  }

  /// 为客户端错误创建异常。
  private IngestConfigurationException createClientErrorException(
      RemoteCallException ex, String code, String operationType) {
    String msg =
        String.format(
            "注册中心客户端错误 provenance [%s] operation [%s]: httpStatus=%d, errorCode=%s, traceId=%s",
            code, operationType, ex.getHttpStatus(), ex.getErrorCode(), ex.getTraceId());
    log.error(msg, ex);
    return new IngestConfigurationException(code, operationType, msg, ex);
  }

  /// 创建最小可用的配置快照。
  private ProvenanceConfigSnapshot createMinimalSnapshot(String provenanceCode) {
    log.info("创建最小溯源快照, code={}", provenanceCode);

    // 创建最小 ProvenanceInfo
    ProvenanceConfigSnapshot.ProvenanceInfo minimalProvenance =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            null, // id
            provenanceCode, // code
            null, // name
            null, // baseUrlDefault
            null, // timezoneDefault
            null, // docsUrl
            true, // active
            null // lifecycleStatusCode
            );

    return new ProvenanceConfigSnapshot(
        minimalProvenance,
        null, // windowOffset
        null, // pagination
        null, // http
        null, // batching
        null, // retry
        null // rateLimit
        );
  }
}
